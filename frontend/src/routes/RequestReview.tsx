import { ReactNode, useContext, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import SyntaxHighlighter from "react-syntax-highlighter";
import ReactMarkdown from "react-markdown";
import {
  ExecutionRequestResponseWithComments,
  addCommentToRequest,
  addReviewToRequest,
  getSingleRequest,
  runQuery,
  ChangeExecutionRequestPayload,
  patchRequest,
  Edit,
  Review,
  Comment as CommentEvent,
  Execute,
  postStartServer,
  ProxyResponse,
  ExecuteResponseResult,
  KubernetesExecutionRequestResponseWithComments,
  DatasourceExecutionRequestResponseWithComments,
  executeCommand,
  KubernetesExecuteResponse,
} from "../api/ExecutionRequestApi";
import Button from "../components/Button";
import { mapStatus, mapStatusToLabelColor, timeSince } from "./Requests";
import { UserStatusContext } from "../components/UserStatusProvider";
import MultiResult from "../components/MultiResult";
import {
  a11yDark,
  a11yLight,
} from "react-syntax-highlighter/dist/esm/styles/hljs";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";
import { CodeProps } from "react-markdown/lib/ast-to-react";
import Spinner from "../components/Spinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../components/InitialBubble";
import { timeAgo } from "./Auditlog";
import ShellResult from "../components/ShellResult";

interface RequestReviewParams {
  requestId: string;
}

const Highlighter = (props: { children: string }) => {
  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const style = currentTheme === "dark" ? a11yDark : a11yLight;
  console.log("Switching theme because" + currentTheme);

  return (
    <SyntaxHighlighter
      style={style}
      language="sql"
      customStyle={{
        background: "transparent",
      }}
      PreTag={"div"}
    >
      {props.children}
    </SyntaxHighlighter>
  );
};

const componentMap = {
  code: ({ children }: CodeProps) => {
    return <Highlighter>{children as string}</Highlighter>;
  },
  ul: ({ children }: { children: ReactNode }) => (
    <ul className="list-disc ml-4 mt-4">{children}</ul>
  ),
};

const useRequest = (id: string) => {
  const [request, setRequest] = useState<
    ExecutionRequestResponseWithComments | undefined
  >(undefined);
  const [loading, setLoading] = useState(true);
  const [proxyResponse, setProxyResponse] = useState<ProxyResponse | undefined>(
    undefined,
  );

  useEffect(() => {
    async function request() {
      setLoading(true);
      const request = await getSingleRequest(id);
      setRequest(request);
      setLoading(false);
    }
    void request();
  }, []);

  const [results, setResults] = useState<ExecuteResponseResult[] | undefined>();
  const [dataLoading, setDataLoading] = useState<boolean>(false);
  const [kubernetesResults, setKubernetesResults] = useState<
    KubernetesExecuteResponse | undefined
  >();
  const [executionError, setExecutionError] = useState<string | undefined>(
    undefined,
  );

  const addComment = async (comment: string) => {
    const event = await addCommentToRequest(id, comment);

    // update the request with the new comment by updating the events propertiy with a new Comment
    setRequest((request) => {
      if (request === undefined) {
        return undefined;
      }
      return {
        ...request,
        events: [...request.events, event],
      };
    });
  };

  const start = async (): Promise<ProxyResponse> => {
    const response = await postStartServer(id);
    setProxyResponse(response);
    return response;
  };

  const updateRequest = async (request: ChangeExecutionRequestPayload) => {
    const newRequest = await patchRequest(id, request);
    setRequest(newRequest);
  };

  const approve = async (comment: string) => {
    await addReviewToRequest(id, comment, "APPROVE");
    const newRequest = await getSingleRequest(id);
    setRequest(newRequest);
  };

  const execute = async (explain: boolean) => {
    setDataLoading(true);
    if (request?._type === "DATASOURCE") {
      const response = await runQuery(id, undefined, explain);
      if (response.results) {
        setResults(response.results);
      } else {
        setExecutionError(response.error?.message);
      }
    } else if (request?._type === "KUBERNETES") {
      const response = await executeCommand(id);
      if (response.results) {
        setKubernetesResults(response.results);
      } else {
        setExecutionError(response.error?.message);
      }
    }

    setDataLoading(false);
  };

  return {
    request,
    addComment,
    approve,
    execute,
    start,
    updateRequest,
    results,
    kubernetesResults,
    dataLoading,
    executionError,
    loading,
    proxyResponse,
  };
};

function RequestReview() {
  const params = useParams() as unknown as RequestReviewParams;
  const {
    request,
    addComment,
    approve,
    execute,
    start,
    updateRequest,
    results,
    kubernetesResults,
    dataLoading,
    executionError,
    loading,
    proxyResponse,
  } = useRequest(params.requestId);

  const navigate = useNavigate();

  const run = async (explain?: boolean) => {
    if (request?.type === "SingleExecution") {
      await execute(explain || false);
    } else {
      navigate(`/requests/${request?.id}/session`);
    }
  };

  return (
    <div>
      {(loading && <Spinner />) ||
        (request && (
          <div className="max-w-3xl m-auto mt-10">
            <h1 className="text-3xl my-2 w-full flex items-start">
              <div className="mr-auto">{request?.title}</div>
              <div
                className={` ${mapStatusToLabelColor(
                  mapStatus(request.reviewStatus, request.executionStatus),
                )} w-min rounded-md px-2 py-1 mt-2 text-base font-medium ring-1 ring-inset `}
              >
                {mapStatus(request.reviewStatus, request.executionStatus)}
              </div>
            </h1>
            <div className="">
              <div className="">
                {request &&
                  (request?._type === "DATASOURCE" ? (
                    <DatasourceRequestDisplay
                      request={request}
                      run={run}
                      start={start}
                      updateRequest={updateRequest}
                      results={results}
                      dataLoading={dataLoading}
                      executionError={executionError}
                      proxyResponse={proxyResponse}
                    ></DatasourceRequestDisplay>
                  ) : (
                    (
                      <KubernetesRequestDisplay
                        request={request}
                        run={run}
                        start={start}
                        updateRequest={updateRequest}
                        results={kubernetesResults}
                        dataLoading={dataLoading}
                        executionError={executionError}
                        proxyResponse={proxyResponse}
                      ></KubernetesRequestDisplay>
                    ) || <></>
                  ))}
                <div className="w-full border-b dark:border-slate-700 border-slate-300 mt-3"></div>
                <div className="mt-6">
                  <span>Activity</span>
                </div>
                <div>
                  {request === undefined
                    ? ""
                    : request?.events?.map((event, index) => {
                        if (event?._type === "EDIT")
                          return (
                            <EditEvent event={event} index={index}></EditEvent>
                          );
                        if (event?._type === "EXECUTE")
                          return (
                            <ExecuteEvent
                              event={event}
                              index={index}
                            ></ExecuteEvent>
                          );

                        return <Comment event={event} index={index}></Comment>;
                      })}
                  <CommentBox
                    addComment={addComment}
                    approve={approve}
                    userId={request?.author?.id}
                  ></CommentBox>
                </div>
              </div>
            </div>
          </div>
        ))}
    </div>
  );
}

function DatasourceRequestDisplay({
  request,
  run,
  start,
  updateRequest,
  results,
  dataLoading,
  executionError,
  proxyResponse,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  run: (explain?: boolean) => Promise<void>;
  start: () => Promise<ProxyResponse>;
  updateRequest: (request: { statement?: string }) => Promise<void>;
  results: ExecuteResponseResult[] | undefined;
  dataLoading: boolean;
  executionError: string | undefined;
  proxyResponse: ProxyResponse | undefined;
}) {
  return (
    <>
      <DatasourceRequestBox
        request={request}
        runQuery={run}
        startServer={start}
        updateRequest={updateRequest}
      ></DatasourceRequestBox>
      <div className="flex justify-center">
        {(dataLoading && <Spinner></Spinner>) ||
          (results && <MultiResult resultList={results}></MultiResult>)}
      </div>
      {executionError && (
        <div className="text-red-500 my-4">{executionError}</div>
      )}
      {proxyResponse && (
        <div className="text-lime-500 my-4">
          Server started on {proxyResponse.port} with username{" "}
          <i>{proxyResponse.username}</i> and password{" "}
          <i>{proxyResponse.password}</i>
        </div>
      )}
    </>
  );
}

function KubernetesRequestDisplay({
  request,
  run,
  start,
  updateRequest,
  results,
  dataLoading,
  executionError,
  proxyResponse,
}: {
  request: KubernetesExecutionRequestResponseWithComments;
  run: (explain?: boolean) => Promise<void>;
  start: () => Promise<ProxyResponse>;
  updateRequest: (request: { command?: string }) => Promise<void>;
  results: KubernetesExecuteResponse | undefined;
  dataLoading: boolean;
  executionError: string | undefined;
  proxyResponse: ProxyResponse | undefined;
}) {
  return (
    <>
      <KubernetesRequestBox
        request={request}
        runQuery={run}
        startServer={start}
        updateRequest={updateRequest}
      ></KubernetesRequestBox>
      <div className="flex justify-center">
        {(dataLoading && <Spinner></Spinner>) ||
          (results && <ShellResult {...results}></ShellResult>)}
      </div>
      {executionError && (
        <div className="text-red-500 my-4">{executionError}</div>
      )}
      {proxyResponse && (
        <div className="text-lime-500 my-4">
          Server started on {proxyResponse.port} with username{" "}
          <i>{proxyResponse.username}</i> and password{" "}
          <i>{proxyResponse.password}</i>
        </div>
      )}
    </>
  );
}

interface KubernetesRequestBoxProps {
  request: KubernetesExecutionRequestResponseWithComments;
  runQuery: (explain?: boolean) => Promise<void>;
  startServer: () => Promise<ProxyResponse>;
  updateRequest: (request: { command?: string }) => Promise<void>;
}

const KubernetesRequestBox: React.FC<KubernetesRequestBoxProps> = ({
  request,
  updateRequest,
  runQuery,
}) => {
  const [editMode, setEditMode] = useState(false);
  const [command, setCommand] = useState(request?.command || "");

  useEffect(() => {
    setCommand(request?.command || "");
  }, [request?.command]);

  const changeCommand = async (
    e: React.MouseEvent<HTMLButtonElement, MouseEvent>,
  ) => {
    e.preventDefault();
    await updateRequest({ command: command });
    setEditMode(false);
  };

  return (
    <div className="relative border-slate-500 dark:bg-slate-950 dark:border dark:border-slate-950">
      <div className="text-slate-800 py-2 text-sm flex bg-slate-50 dark:bg-slate-950 dark:text-slate-50 dark:border-none">
        <div>
          {request?.author.fullName} wants to execute a Kubernetes command in:
          <span className="italic"> {request?.connection.displayName}</span>
        </div>
        <div className="ml-auto dark:text-slate-500">
          {timeAgo(new Date(request?.createdAt ?? ""))}
        </div>
      </div>
      <div className="py-3 px-4">
        <p className="text-slate-500 pb-6">{request?.description}</p>
        <div className="text-slate-500">
          Namespace: <strong>{request?.namespace}</strong>
          <br />
          Pod Name: <strong>{request?.podName}</strong>
          <br />
          Container Name: <strong>{request?.containerName || "Default"}</strong>
          <br />
          Command:{" "}
          {editMode ? (
            <textarea
              className="appearance-none block w-full text-gray-700 border border-gray-200 bg-slate-100 focus:bg-white dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors dark:text-slate-50 p-1 rounded-md leading-normal mb-2 focus:outline-none focus:border-gray-500"
              rows={3}
              onChange={(e) => setCommand(e.target.value)}
              value={command}
            ></textarea>
          ) : (
            <Highlighter>{command || "No command specified"}</Highlighter>
          )}
        </div>
        {editMode ? (
          <div className="flex justify-end mt-2">
            <Button className="mr-2" onClick={() => setEditMode(false)}>
              Cancel
            </Button>
            <Button onClick={(e) => void changeCommand(e)}>Save</Button>
          </div>
        ) : (
          <Button className="mt-2" onClick={() => setEditMode(true)}>
            Edit Command
          </Button>
        )}
      </div>
      <div className="relative ml-4 flex justify-end">
        <Button
          className="mt-3"
          id="runQuery"
          type={(request?.reviewStatus == "APPROVED" && "submit") || "disabled"}
          onClick={() => void runQuery(false)}
        >
          <div
            className={`play-triangle inline-block w-2 h-3 mr-2 ${
              (request?.reviewStatus == "APPROVED" && "bg-slate-50") ||
              "bg-slate-500"
            }`}
          ></div>
          {request?.type == "SingleExecution" ? "Run Command" : "Start Session"}
        </Button>
      </div>
    </div>
  );
};

function DatasourceRequestBox({
  request,
  runQuery,
  startServer,
  updateRequest,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  runQuery: (explain?: boolean) => Promise<void>;
  startServer: () => Promise<ProxyResponse>;
  updateRequest: (request: { statement?: string }) => Promise<void>;
}) {
  const [editMode, setEditMode] = useState(false);
  const [statement, setStatement] = useState(request?.statement || "");
  const changeStatement = async (
    e: React.MouseEvent<HTMLButtonElement, MouseEvent>,
  ) => {
    e.preventDefault();
    await updateRequest({ statement });
    setEditMode(false);
  };
  useEffect(() => {
    setStatement(request?.statement || "");
  }, [request?.statement]);

  const questionText =
    request?.type == "SingleExecution"
      ? " wants to execute a statement on "
      : " wants to have access to ";

  return (
    <div>
      <div className="relative border-slate-500 dark:bg-slate-950 dark:border dark:border-slate-950">
        <InitialBubble name={request?.author.fullName} />
        <p className="text-slate-800 py-2 text-sm flex bg-slate-50 dark:bg-slate-950 dark:text-slate-50 dark:border-none">
          <div>
            {request?.author?.fullName + questionText}
            <span className="italic">{request?.connection.displayName}</span>
          </div>
          <div className="ml-auto dark:text-slate-500">
            {timeAgo(new Date(request?.createdAt ?? ""))}
          </div>
        </p>
        <div className="py-3">
          <p className="text-slate-500 pb-6">{request?.description}</p>
          {request?.type == "SingleExecution" ? (
            editMode ? (
              <div>
                <textarea
                  className="appearance-none block w-full text-gray-700 border border-gray-200 bg-slate-100 focus:bg-white dark:bg-slate-900 dark:border-slate-700 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors dark:text-slate-50 p-1 rounded-md leading-normal mb-2 focus:outline-none focus:border-gray-500"
                  id="statement"
                  name="statement"
                  rows={4}
                  onChange={(event) => setStatement(event.target.value)}
                  value={statement}
                ></textarea>
                <div className="flex justify-end">
                  <Button
                    className="mr-2"
                    type="reset"
                    onClick={() => {
                      setEditMode(false);
                    }}
                  >
                    Cancel
                  </Button>
                  <Button
                    type="submit"
                    onClick={(e) => void changeStatement(e)}
                  >
                    Save
                  </Button>
                </div>
              </div>
            ) : (
              <div
                className="dark:bg-slate-950 border dark:border-slate-700 rounded border-slate-300 dark:hover:border-slate-500 transition-colors"
                onClick={() => setEditMode(true)}
              >
                <Highlighter>
                  {request === undefined ? "404" : request.statement || ""}
                </Highlighter>
              </div>
            )
          ) : (
            ""
          )}
        </div>
      </div>
      <div className="relative ml-4 flex justify-end">
        <Button
          className="mt-3 mr-4"
          id="runQuery"
          type={
            (request?.reviewStatus == "AWAITING_APPROVAL" && "submit") ||
            (request?.reviewStatus == "APPROVED" && "submit") ||
            "disabled"
          }
          onClick={() => void runQuery(true)}
        >
          Explain
        </Button>
        <Button
          className="mt-3"
          id="runQuery"
          type={(request?.reviewStatus == "APPROVED" && "submit") || "disabled"}
          onClick={() => void runQuery()}
        >
          <div
            className={`play-triangle inline-block w-2 h-3 mr-2 ${
              (request?.reviewStatus == "APPROVED" && "bg-slate-50") ||
              "bg-slate-500"
            }`}
          ></div>
          {request?.type == "SingleExecution" ? "Run Query" : "Start Session"}
        </Button>
        {(request?.type == "TemporaryAccess" && (
          <Button
            className="mt-3 ml-2"
            id="startServer"
            type={
              (request?.reviewStatus == "APPROVED" && "submit") || "disabled"
            }
            onClick={() => void startServer()}
          >
            Start Proxy
          </Button>
        )) ||
          ""}
      </div>
    </div>
  );
}

function EditEvent({ event, index }: { event: Edit; index: number }) {
  return (
    <div>
      <div className="relative py-4 ml-4 flex">
        {(!(index === 0) && (
          <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
            {" "}
          </div>
        )) || (
          <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-5 bottom-0">
            {" "}
          </div>
        )}
        <div className="h-4 w-4 -ml-1 pb-6 mr-2 inline-block align-text-bottom items-center dark:bg-slate-950 bg-slate-50 fill-slate-950 dark:fill-slate-50 z-0">
          <div className="inline pr-2 dark:text-slate-500 text-slate-900 text-xs">
            <FontAwesomeIcon icon={solid("pen")} />
          </div>
        </div>
        <div className="text-slate-500 text-sm">
          {event?.author?.fullName} edited:
        </div>
      </div>
      <div className="relative shadow-md dark:shadow-none dark:border-slate-700 rounded-md border">
        <InitialBubble name={event?.author?.fullName} />
        <p className="text-slate-500 dark:text-slate-500 px-4 pt-2 text-sm flex justify-between dark:bg-slate-900 rounded-t-md">
          <div className="mr-4">
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
          {event?.previousQuery && (
            <div>
              <p>Previous Statement</p>
            </div>
          )}
          {event?.previousCommand && (
            <div>
              <p>Previous Command</p>
            </div>
          )}
        </p>
        {event?.previousQuery && (
          <div className="py-3 px-4 dark:bg-slate-900 rounded-b-md">
            <Highlighter>{event.previousQuery}</Highlighter>
          </div>
        )}
        {event?.previousCommand && (
          <div className="py-3 px-4 dark:bg-slate-900 rounded-b-md">
            <Highlighter>{event.previousCommand}</Highlighter>
          </div>
        )}
      </div>
    </div>
  );
}

function ExecuteEvent({ event, index }: { event: Execute; index: number }) {
  return (
    <div>
      <div className="relative py-4 ml-4 flex">
        {(!(index === 0) && (
          <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
            {" "}
          </div>
        )) || (
          <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-5 bottom-0">
            {" "}
          </div>
        )}
        <div className="h-4 w-4 -ml-0.5 pb-6 mr-2 inline-block align-text-bottom items-center dark:bg-slate-950 bg-slate-50 fill-slate-950 dark:fill-slate-50 z-0">
          <div className="inline pr-2 text-green-600 text-xs">
            <FontAwesomeIcon icon={solid("play")} />
          </div>
        </div>
        {event?.query && (
          <div className="text-slate-500 text-sm">
            {event?.author?.fullName} ran the following statement:
          </div>
        )}
        {event?.command && (
          <div className="text-slate-500 text-sm">
            {event?.author?.fullName} ran the following command:
          </div>
        )}
      </div>
      <div className="relative shadow-md dark:shadow-none dark:border-slate-700 rounded-md border">
        <InitialBubble name={event?.author?.fullName} />
        <p className="text-slate-500 dark:text-slate-500 px-4 pt-2 text-sm flex justify-between dark:bg-slate-900 rounded-t-md">
          <div className="mr-4">
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
        </p>
        {event?.query && (
          <div className="py-3 px-4 dark:bg-slate-900 rounded-b-md">
            <Highlighter>{event.query}</Highlighter>
          </div>
        )}
        {event?.command && (
          <div className="py-3 px-4 dark:bg-slate-900 rounded-b-md">
            <Highlighter>{event.command}</Highlighter>
          </div>
        )}
      </div>
    </div>
  );
}

function Comment({
  event,
  index,
}: {
  event: Review | CommentEvent;
  index: number;
}) {
  console.log(event);
  return (
    <div>
      <div className="relative py-4 ml-4 flex">
        {(!(index === 0) && (
          <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
            {" "}
          </div>
        )) || (
          <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-5 bottom-0">
            {" "}
          </div>
        )}
        {event?._type === "COMMENT" ? (
          <svg className="h-4 w-4 -ml-2 mr-2 mt-0.5 inline-block align-text-bottom items-center dark:bg-slate-950 bg-slate-50 fill-slate-950 dark:fill-slate-50 z-0">
            <path d="M11.93 8.5a4.002 4.002 0 0 1-7.86 0H.75a.75.75 0 0 1 0-1.5h3.32a4.002 4.002 0 0 1 7.86 0h3.32a.75.75 0 0 1 0 1.5Zm-1.43-.75a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z"></path>
          </svg>
        ) : (
          <div className="h-4 w-4 -ml-1 pb-6 mr-2 mt-0.5 inline-block align-text-bottom items-center dark:bg-slate-950 bg-slate-50 fill-slate-950 dark:fill-slate-50 z-0">
            <div className="text-green-600 inline pr-2">
              <FontAwesomeIcon icon={solid("check")} />
            </div>
          </div>
        )}
        <div className="text-slate-500 text-sm">
          {event?._type === "COMMENT" ? (
            <div>{`${event?.author?.fullName} commented:`}</div>
          ) : (
            <div>{`${event?.author?.fullName} approved`} </div>
          )}
        </div>
      </div>
      <div className="relative shadow-md dark:shadow-none dark:border-slate-700 rounded-md border">
        <InitialBubble name={event?.author?.fullName} />
        <p className="text-slate-500 dark:text-slate-500 px-4 pt-2 text-sm flex justify-between dark:bg-slate-900 rounded-t-md">
          <div>
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
        </p>
        <div className="py-3 px-4 dark:bg-slate-900 rounded-b-md">
          <ReactMarkdown components={componentMap}>
            {event.comment}
          </ReactMarkdown>
        </div>
      </div>
    </div>
  );
}

function CommentBox({
  addComment,
  approve,
  userId,
}: {
  addComment: (comment: string) => Promise<void>;
  approve: (comment: string) => Promise<void>;
  userId?: string;
}) {
  const [commentFormVisible, setCommentFormVisible] = useState<boolean>(true);
  const [comment, setComment] = useState<string>("");

  const userContext = useContext(UserStatusContext);

  const handleAddComment = async () => {
    await addComment(comment);
    setComment("");
  };

  const handleApprove = async () => {
    await approve(comment);
    setComment("");
  };

  const isOwnRequest =
    userContext.userStatus && userContext.userStatus?.id === userId;
  return (
    <div>
      <div className="relative py-4 ml-4">
        <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
          {" "}
        </div>
      </div>
      <div className=" dark:border-slate-700 rounded-md dark:border shadow-md relative mb-5">
        <InitialBubble
          name={
            (userContext.userStatus && userContext.userStatus?.fullName) || ""
          }
        />
        <div className="mb-2 border-b-slate-300 border dark:border-b dark:border-t-0 dark:border-l-0 dark:border-r-0 dark:bg-slate-900 dark:border-slate-700 rounded-t-md">
          <div className="-mb-px z-10 overflow-auto">
            <button
              className={`mt-2 ml-2 ${
                commentFormVisible
                  ? "border border-b-slate-50 bg-slate-50 dark:bg-slate-950 dark:text-slate-50 dark:border-slate-700 dark:border-b-slate-950"
                  : "dark:hover:bg-slate-800"
              }  border-slate-300 px-4 py-2 text-sm text-slate-600 leading-6 rounded-t-md`}
              onClick={() => setCommentFormVisible(true)}
            >
              write
            </button>
            <button
              className={`mt-2 ${
                commentFormVisible
                  ? "dark:hover:bg-slate-800"
                  : "border border-b-white bg-white dark:bg-slate-950 dark:text-slate-50 dark:border-slate-700 dark:border-b-slate-950"
              } border-slate-300  px-4 py-2 text-sm text-slate-600 leading-6 rounded-t-md`}
              onClick={() => setCommentFormVisible(false)}
            >
              preview
            </button>
          </div>
        </div>
        <div className="px-3">
          {commentFormVisible ? (
            <textarea
              className="appearance-none block w-full text-gray-700 border border-slate-100 shadow-sm bg-slate-50 focus:shadow-md p-1 rounded-md leading-normal mb-2 focus:outline-none dark:bg-slate-950 dark:border-slate-700 dark:text-slate-50 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors"
              id="comment"
              name="comment"
              rows={4}
              onChange={(event) => setComment(event.target.value)}
              value={comment}
              placeholder="Leave a comment"
            ></textarea>
          ) : (
            <ReactMarkdown
              className="h-28 max-h-48 overflow-y-scroll scrollbar-thin scrollbar-track-slate-100  scrollbar-thumb-slate-300 scrollbar-thumb-rounded-md scrollbar-track-rounded-md border-r-slate-300 my-2"
              components={componentMap}
            >
              {comment}
            </ReactMarkdown>
          )}
          <div className="p-1">
            <div className="flex justify-end mb-2">
              <Button
                className="mr-2"
                id="addComment"
                onClick={() => void handleAddComment()}
              >
                Add Comment
              </Button>
              <Button
                id="approve"
                type={`${isOwnRequest ? "disabled" : "submit"}`}
                onClick={() => void handleApprove()}
              >
                Approve
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default RequestReview;
