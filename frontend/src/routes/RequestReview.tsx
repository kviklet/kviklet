import { ReactNode, useContext, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import SyntaxHighlighter from "react-syntax-highlighter";
import ReactMarkdown from "react-markdown";
import {
  Edit,
  Review,
  Comment as CommentEvent,
  Execute,
  ProxyResponse,
  ExecuteResponseResult,
  KubernetesExecutionRequestResponseWithComments,
  DatasourceExecutionRequestResponseWithComments,
  KubernetesExecuteResponse,
  SQLDumpRequest,
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
import ShellResult from "../components/ShellResult";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import MenuDropDown from "../components/MenuDropdown";
import baseUrl from "../api/base";
import LoadingCancelButton from "../components/LoadingCancelButton";
import useRequest, {
  ReviewTypes,
  isRelationalDatabase,
} from "../hooks/request";
import Modal from "../components/Modal";
import SQLDumpConfirm from "../components/SQLDumpConfirm";
import { ConnectionResponse } from "../api/DatasourceApi";
import useNotification from "../hooks/useNotification";

interface RequestReviewParams {
  requestId: string;
}

const Highlighter = ({
  children,
  language = "sql",
}: {
  children: string;
  language?: string;
}) => {
  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const style = currentTheme === "dark" ? a11yDark : a11yLight;

  return (
    <SyntaxHighlighter
      style={style}
      language={language}
      customStyle={{
        background: "transparent",
      }}
      PreTag={"div"}
    >
      {children}
    </SyntaxHighlighter>
  );
};

const componentMap = {
  code: ({ children }: CodeProps) => {
    return <Highlighter>{children as string}</Highlighter>;
  },
  ul: ({ children }: { children: ReactNode }) => (
    <ul className="ml-4 mt-4 list-disc">{children}</ul>
  ),
};

function RequestReview() {
  const params = useParams() as unknown as RequestReviewParams;
  const {
    request,
    sendReview,
    execute,
    cancelQuery,
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
          <div className="m-auto mt-10 max-w-3xl">
            <h1 className="my-2 flex w-full items-start text-3xl">
              <div className="mr-auto">{request?.title}</div>
              <div
                className={` ${mapStatusToLabelColor(
                  mapStatus(request.reviewStatus, request.executionStatus),
                )} mt-2 rounded-md px-2 py-1 text-base font-medium ring-1 ring-inset `}
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
                      cancelQuery={cancelQuery}
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
                <div className="mt-3 w-full border-b border-slate-300 dark:border-slate-700"></div>
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
                        if (event?._type === "COMMENT")
                          return (
                            <Comment event={event} index={index}></Comment>
                          );
                        if (event?._type === "REVIEW")
                          return (
                            <ReviewEvent
                              event={event}
                              index={index}
                            ></ReviewEvent>
                          );
                      })}
                  <CommentBox
                    sendReview={sendReview}
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
  cancelQuery,
  updateRequest,
  results,
  dataLoading,
  executionError,
  proxyResponse,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  run: (explain?: boolean) => Promise<void>;
  cancelQuery: () => Promise<void>;
  start: () => Promise<void>;
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
        cancelQuery={cancelQuery}
        startServer={start}
        updateRequest={updateRequest}
      ></DatasourceRequestBox>
      <div className="mt-4 flex justify-center">
        {(dataLoading && <Spinner></Spinner>) ||
          (results && <MultiResult resultList={results}></MultiResult>)}
      </div>
      {executionError && (
        <div className="my-4 text-red-500">{executionError}</div>
      )}
      {proxyResponse && (
        <div className="my-4 text-lime-500">
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
  start: () => Promise<void>;
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
        <div className="my-4 text-red-500">{executionError}</div>
      )}
      {proxyResponse && (
        <div className="my-4 text-lime-500">
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
  startServer: () => Promise<void>;
  updateRequest: (request: { command?: string }) => Promise<void>;
}

const KubernetesRequestBox: React.FC<KubernetesRequestBoxProps> = ({
  request,
  updateRequest,
  runQuery,
}) => {
  const [editMode, setEditMode] = useState(false);
  const [command, setCommand] = useState(request?.command || "");

  const navigate = useNavigate();

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

  const navigateCopy = () => {
    navigate(`/new`, {
      state: {
        connectionId: request?.connection.id,
        connectionType: "Kubernetes",
        title: request?.title,
        mode: request?.type,
        description: request?.description,
        command: request?.command,
        namespace: request?.namespace,
        podName: request?.podName,
        containerName: request?.containerName,
      },
    });
  };

  const menuDropDownItems = [
    {
      onClick: () => {
        void navigateCopy();
      },
      enabled: true,
      content: "Copy Request",
    },
  ];

  return (
    <div className="relative border-slate-500 dark:border dark:border-slate-950 dark:bg-slate-950">
      <div className="flex bg-slate-50 py-2 text-sm text-slate-800 dark:border-none dark:bg-slate-950 dark:text-slate-50">
        <div>
          {request?.author.fullName} wants to execute a Kubernetes command in:
          <span className="italic"> {request?.connection.displayName}</span>
        </div>
        <div className="ml-auto dark:text-slate-500">
          {timeSince(new Date(request?.createdAt ?? ""))}
        </div>
      </div>
      <div className="px-4 py-3">
        <p className="pb-6 text-slate-500">{request?.description}</p>
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
              className="mb-2 block w-full appearance-none rounded-md border border-gray-200 bg-slate-100 p-1 leading-normal text-gray-700 transition-colors focus:border-gray-500 focus:bg-white focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 dark:focus:border-slate-500 dark:hover:border-slate-600 dark:focus:hover:border-slate-500"
              rows={3}
              onChange={(e) => setCommand(e.target.value)}
              value={command}
            ></textarea>
          ) : (
            <Highlighter>{command || "No command specified"}</Highlighter>
          )}
        </div>
        {editMode ? (
          <div className="mt-2 flex justify-end">
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
      <div className="relative mt-3 flex justify-end">
        <MenuDropDown items={menuDropDownItems}></MenuDropDown>
        <Button
          className=""
          id="runQuery"
          type={(request?.reviewStatus == "APPROVED" && "submit") || "disabled"}
          onClick={() => void runQuery(false)}
        >
          <div
            className={`play-triangle mr-2 inline-block h-3 w-2 ${
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
  cancelQuery,
  startServer,
  updateRequest,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  runQuery: (explain?: boolean) => Promise<void>;
  cancelQuery: () => Promise<void>;
  startServer: () => Promise<void>;
  updateRequest: (request: { statement?: string }) => Promise<void>;
}) {
  const [editMode, setEditMode] = useState(false);
  const { addNotification } = useNotification();
  const [showSQLDumpModal, setShowSQLDumpModal] = useState(false);
  const [chosenConnection, setChosenConnection] = useState<
    ConnectionResponse | undefined
  >(undefined);
  const navigate = useNavigate();
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
      : request?.type == "TemporaryAccess"
      ? " wants to have access to "
      : " wants to get a SQL dump from ";

  const navigateCopy = () => {
    navigate(`/new`, {
      state: {
        connectionId: request?.connection.id,
        connectionType: "Datasource",
        title: request?.title,
        mode: request?.type,
        description: request?.description,
        statement: request?.statement,
      },
    });
  };

  const menuDropDownItems = [
    {
      onClick: () => {},
      enabled: request?.csvDownload?.allowed || false,
      tooltip:
        (!request?.csvDownload?.allowed && request?.csvDownload?.reason) ||
        undefined,
      content: (
        <a href={`${baseUrl}/execution-requests/${request?.id}/download`}>
          Download as CSV
        </a>
      ),
    },
    {
      onClick: () => {
        void navigateCopy();
      },
      enabled: true,
      content: "Copy Request",
    },
    ...(request?.type == "SingleExecution"
      ? [
          {
            onClick: () => {
              void runQuery(true);
            },
            enabled:
              isRelationalDatabase(request) &&
              (request?.reviewStatus === "APPROVED" ||
                request?.reviewStatus === "AWAITING_APPROVAL"),
            content: "Explain",
          },
        ]
      : []),
    ...(request?.type == "TemporaryAccess"
      ? [
          {
            onClick: () => {
              void startServer();
            },
            enabled: request?.reviewStatus === "APPROVED",
            content: "Start Proxy",
          },
        ]
      : []),
  ];

  const fileHandler = async (connectionId: string) => {
    try {
      // Create a handle for the file the user wants to save
      const fileHandle = await window.showSaveFilePicker({
        suggestedName: `${connectionId}.sql`,
        types: [
          {
            description: "SQL Files",
            accept: {
              "text/sql": [".sql"],
            },
          },
        ],
      });
      return fileHandle;
    } catch (error) {
      console.error("Error getting file handle:", error);
      throw error;
    }
  };

  // Function to handle streaming the SQL dump and saving it to a file
  const handleStreamSQLDump = async (connectionId: string) => {
    try {
      // Get a file handle to write the file
      const fileHandle = await fileHandler(connectionId);
      // Request and decode the SQL dump data
      const decodedChunks = await SQLDumpRequest(connectionId);

      // Create a ReadableStream from decoded chunks
      const readableStream = new ReadableStream<Uint8Array>({
        start(controller) {
          decodedChunks.forEach((chunk: Uint8Array) => {
            controller.enqueue(chunk);
          });
          controller.close();
        },
      });

      const reader = readableStream.getReader();
      // Create a writable stream to write data to the file
      const writableStream = await fileHandle.createWritable();

      // Handle reading from the readable stream and writing to the writable stream
      const pump = async () => {
        let done = false;
        while (!done) {
          const result = await reader.read();
          done = result.done;
          const value = result.value;
          if (value !== undefined) {
            await writableStream.write(value);
          }
        }
        await writableStream.close();
      };

      await pump();
      addNotification({
        title: "Success",
        text: "SQL dump file saved successfully.",
        type: "info",
      });
    } catch (error) {
      if (error instanceof Error) {
        addNotification({
          title: "Failed to process SQL dump.",
          text: error.message,
          type: "error",
        });
      }
    } finally {
      setShowSQLDumpModal(false);
    }
  };

  const SQLDumpModal = () => {
    if (!showSQLDumpModal || !chosenConnection) return null;
    return (
      <Modal setVisible={setShowSQLDumpModal}>
        <SQLDumpConfirm
          title="Get SQL Dump"
          message={`Are you sure you want to get sql dump from database ${chosenConnection?.displayName}?`}
          onConfirm={() => handleStreamSQLDump(chosenConnection.id)} //Export Databse Request Streamed
          onCancel={() => setShowSQLDumpModal(false)}
        />
      </Modal>
    );
  };

  const handleButtonClick = async () => {
    if (request?.type === "SQLDump") {
      setChosenConnection(request.connection);
      setShowSQLDumpModal(true);
    } else {
      await runQuery();
    }
  };

  return (
    <div>
      <div className="relative border-slate-500 dark:border dark:border-slate-950 dark:bg-slate-950">
        <InitialBubble name={request?.author.fullName} />
        <div className="flex bg-slate-50 py-2 text-sm text-slate-800 dark:border-none dark:bg-slate-950 dark:text-slate-50">
          <div>
            {request?.author?.fullName + questionText}
            <span className="italic">{request?.connection.displayName}</span>
          </div>
          <div className="ml-auto dark:text-slate-500">
            {timeSince(new Date(request?.createdAt ?? ""))}
          </div>
        </div>
        <div className="py-3">
          <p className="pb-6 text-slate-500">{request?.description}</p>
          {request?.type == "SingleExecution" ? (
            editMode ? (
              <div>
                <textarea
                  className="mb-2 block w-full appearance-none rounded-md border border-gray-200 bg-slate-100 p-1 leading-normal text-gray-700 transition-colors focus:border-gray-500 focus:bg-white focus:outline-none dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 dark:focus:border-slate-500 dark:hover:border-slate-600 dark:focus:hover:border-slate-500"
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
                className="rounded border border-slate-300 transition-colors dark:border-slate-700 dark:bg-slate-950 dark:hover:border-slate-500"
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
      <div className="relative mt-3 flex justify-end">
        <MenuDropDown items={menuDropDownItems}></MenuDropDown>
        {isRelationalDatabase(request) ? (
          <LoadingCancelButton
            className=""
            id="runQuery"
            type="submit"
            disabled={request?.reviewStatus !== "APPROVED"}
            onClick={handleButtonClick}
            onCancel={() => void cancelQuery()}
          >
            <div
              className={`play-triangle mr-2 inline-block h-3 w-2 ${
                (request?.reviewStatus == "APPROVED" && "bg-slate-50") ||
                "bg-slate-500"
              }`}
            ></div>
            {request?.type === "SingleExecution"
              ? "Run Query"
              : request?.type === "TemporaryAccess"
              ? "Start Session"
              : "Get SQL Dump"}
          </LoadingCancelButton>
        ) : (
          <Button
            className=""
            id="runQuery"
            type={
              (request?.reviewStatus == "APPROVED" && "submit") || "disabled"
            }
            onClick={() => void runQuery()}
          >
            <div
              className={`play-triangle mr-2 inline-block h-3 w-2 ${
                (request?.reviewStatus == "APPROVED" && "bg-slate-50") ||
                "bg-slate-500"
              }`}
            ></div>
            {request?.type == "SingleExecution" ? "Run Query" : "Start Session"}
          </Button>
        )}
        {SQLDumpModal()}
      </div>
    </div>
  );
}

function EditEvent({ event, index }: { event: Edit; index: number }) {
  return (
    <div>
      <div className="relative ml-4 flex py-4">
        {(!(index === 0) && (
          <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )) || (
          <div className="absolute bottom-0 left-0 top-5 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )}
        <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
          <div className="inline pr-2 text-xs text-slate-900 dark:text-slate-500">
            <FontAwesomeIcon icon={solid("pen")} />
          </div>
        </div>
        <div className="text-sm text-slate-500">
          {event?.author?.fullName} edited:
        </div>
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <p className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
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
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.previousQuery}</Highlighter>
          </div>
        )}
        {event?.previousCommand && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.previousCommand}</Highlighter>
          </div>
        )}
      </div>
    </div>
  );
}

function ExecuteEvent({ event, index }: { event: Execute; index: number }) {
  const isDownload = event.isDownload || false;
  const sqlStatementText = isDownload
    ? "downloaded the results for the following statement:"
    : "executed the following statement:";
  return (
    <div className="">
      <div className="relative ml-4 flex py-4">
        {(!(index === 0) && (
          <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )) || (
          <div className="absolute bottom-0 left-0 top-5 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )}
        <div className="z-0 -ml-0.5 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
          <div className="inline pr-2 text-xs text-green-600">
            <FontAwesomeIcon icon={solid("play")} />
          </div>
        </div>
        {event?.query && (
          <div className="text-sm text-slate-500">
            {event?.author?.fullName} {sqlStatementText}
          </div>
        )}
        {event?.command && (
          <div className="text-sm text-slate-500">
            {event?.author?.fullName} ran the following command:
          </div>
        )}
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:bg-slate-900 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <div className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div className="mr-4">
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
        </div>
        {event?.query && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.query}</Highlighter>
          </div>
        )}

        {event?.command && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.command}</Highlighter>
          </div>
        )}
        {event.results.length > 0 && (
          <div className="px-4 dark:bg-slate-900">
            <Disclosure defaultOpen={true}>
              {({ open }) => (
                <>
                  <Disclosure.Button className="w-full py-2 ">
                    <div className="flex w-full flex-row justify-start">
                      <p className="text-xs">Results</p>
                      {open ? (
                        <ChevronDownIcon className="h-4 w-4 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                      ) : (
                        <ChevronRightIcon className="h-4 w-4 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                      )}
                    </div>
                  </Disclosure.Button>
                  <Disclosure.Panel>
                    <div className="mb-2 flex flex-col space-y-2 text-sm dark:text-slate-300">
                      {event.results.map((result, index) => {
                        const renderResult = () => {
                          if (result.type === "QUERY") {
                            return (
                              <div className="flex justify-between">
                                <span>
                                  Returned {result.columnCount} Column(s) with{" "}
                                  {result.rowCount} row(s).
                                </span>
                              </div>
                            );
                          } else if (result.type === "ERROR") {
                            return (
                              <div className="flex justify-between text-red-500">
                                <span>
                                  Query resulted in Error "{result.message}""
                                  with code "{result.errorCode}"".
                                </span>
                              </div>
                            );
                          } else if (result.type === "UPDATE") {
                            return (
                              <div className="flex justify-between">
                                <span>
                                  Statement affected {result.rowsUpdated}{" "}
                                  row(s).
                                </span>
                              </div>
                            );
                          }
                        };
                        return <div key={index}>{renderResult()}</div>;
                      })}
                    </div>
                  </Disclosure.Panel>
                </>
              )}
            </Disclosure>
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
  return (
    <div>
      <div className="relative ml-4 flex py-4">
        {(!(index === 0) && (
          <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )) || (
          <div className="absolute bottom-0 left-0 top-5 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )}
        {event?._type === "COMMENT" ? (
          <svg className="z-0 -ml-2 mr-2 mt-0.5 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <path d="M11.93 8.5a4.002 4.002 0 0 1-7.86 0H.75a.75.75 0 0 1 0-1.5h3.32a4.002 4.002 0 0 1 7.86 0h3.32a.75.75 0 0 1 0 1.5Zm-1.43-.75a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z"></path>
          </svg>
        ) : (
          <div className="z-0 -ml-1 mr-2 mt-0.5 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-green-600">
              <FontAwesomeIcon icon={solid("check")} />
            </div>
          </div>
        )}
        <div className="text-sm text-slate-500">
          {event?._type === "COMMENT" ? (
            <div>{`${event?.author?.fullName} commented:`}</div>
          ) : (
            <div>{`${event?.author?.fullName} approved`} </div>
          )}
        </div>
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <p className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div>
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
        </p>
        <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
          <ReactMarkdown components={componentMap}>
            {event.comment}
          </ReactMarkdown>
        </div>
      </div>
    </div>
  );
}

function ReviewEvent({ event, index }: { event: Review; index: number }) {
  const notificationText = (): JSX.Element => {
    switch (event.action) {
      case "APPROVE":
        return (
          <div className="text-sm text-slate-500">
            {event.author?.fullName} approved
          </div>
        );
      case "REJECT":
        return (
          <div className="text-sm text-red-500">
            {event.author?.fullName} rejected
          </div>
        );
      case "REQUEST_CHANGE":
        return (
          <div className="text-sm text-red-500">
            {event.author?.fullName} requested changes
          </div>
        );
    }
  };

  const notificationIcon = (): JSX.Element => {
    switch (event.action) {
      case "APPROVE":
        return (
          <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-green-600">
              <FontAwesomeIcon icon={solid("check")} />
            </div>
          </div>
        );
      case "REJECT":
        return (
          <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-red-500">
              <FontAwesomeIcon icon={solid("times")} />
            </div>
          </div>
        );
      case "REQUEST_CHANGE":
        return (
          <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-red-500">
              <FontAwesomeIcon icon={solid("pen")} />
            </div>
          </div>
        );
    }
  };
  return (
    <div>
      <div className="relative ml-4 flex py-4">
        {(!(index === 0) && (
          <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )) || (
          <div className="absolute bottom-0 left-0 top-5 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )}
        <div className="flex justify-center align-middle">
          {notificationIcon()}
          {notificationText()}
        </div>
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <p className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div>
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
        </p>
        <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
          <ReactMarkdown components={componentMap}>
            {event.comment}
          </ReactMarkdown>
        </div>
      </div>
    </div>
  );
}

function CommentBox({
  sendReview,
  userId,
}: {
  sendReview: (comment: string, type: ReviewTypes) => Promise<boolean>;
  userId?: string;
}) {
  const [commentFormVisible, setCommentFormVisible] = useState<boolean>(true);
  const [comment, setComment] = useState<string>("");

  const [chosenReviewType, setChosenReviewType] = useState<ReviewTypes>(
    ReviewTypes.Comment,
  );

  const userContext = useContext(UserStatusContext);

  const handleReview = async () => {
    const result = await sendReview(comment, chosenReviewType);
    if (result) {
      setComment("");
    }
  };

  const isOwnRequest =
    userContext.userStatus && userContext.userStatus?.id === userId;

  const reviewTypes = [
    {
      id: ReviewTypes.Comment,
      title: "Comment",
      description: "Submit a general comment without explicit approval",
      enabled: true,
      danger: false,
    },
    {
      id: ReviewTypes.Approve,
      title: "Approve",
      description: "Give your approval to execute this request",
      enabled: !isOwnRequest,
      danger: false,
    },
    {
      id: ReviewTypes.RequestChange,
      title: "Request Changes",
      description:
        "Request a change on this Request, you can later approve it again",
      enabled: !isOwnRequest,
      danger: true,
    },
    {
      id: ReviewTypes.Reject,
      title: "Reject",
      description: "Reject this request from ever executing",
      enabled: !isOwnRequest,
      danger: true,
    },
  ];
  return (
    <div>
      <div className="relative ml-4 py-4">
        <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
          {" "}
        </div>
      </div>
      <div className=" relative mb-5 rounded-md shadow-md dark:border dark:border-slate-700">
        <InitialBubble
          name={
            (userContext.userStatus && userContext.userStatus?.fullName) || ""
          }
        />
        <div className="mb-2 rounded-t-md border border-b-slate-300 dark:border-b dark:border-l-0 dark:border-r-0 dark:border-t-0 dark:border-slate-700 dark:bg-slate-900">
          <div className="z-10 -mb-px overflow-auto">
            <button
              className={`ml-2 mt-2 ${
                commentFormVisible
                  ? "border border-b-slate-50 bg-slate-50 dark:border-slate-700 dark:border-b-slate-950 dark:bg-slate-950 dark:text-slate-50"
                  : "dark:hover:bg-slate-800"
              }  rounded-t-md border-slate-300 px-4 py-2 text-sm leading-6 text-slate-600`}
              onClick={() => setCommentFormVisible(true)}
            >
              write
            </button>
            <button
              className={`mt-2 ${
                commentFormVisible
                  ? "dark:hover:bg-slate-800"
                  : "border border-b-white bg-white dark:border-slate-700 dark:border-b-slate-950 dark:bg-slate-950 dark:text-slate-50"
              } rounded-t-md  border-slate-300 px-4 py-2 text-sm leading-6 text-slate-600`}
              onClick={() => setCommentFormVisible(false)}
            >
              preview
            </button>
          </div>
        </div>
        <div className="px-3">
          {commentFormVisible ? (
            <textarea
              className="mb-2 block w-full appearance-none rounded-md border border-slate-100 bg-slate-50 p-1 leading-normal text-gray-700 shadow-sm transition-colors focus:shadow-md focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50 dark:focus:border-slate-500 dark:hover:border-slate-600 dark:focus:hover:border-slate-500"
              id="comment"
              name="comment"
              rows={4}
              onChange={(event) => setComment(event.target.value)}
              value={comment}
              placeholder="Leave a comment"
            ></textarea>
          ) : (
            <ReactMarkdown
              className="my-2 h-28 max-h-48 overflow-y-scroll border-r-slate-300  scrollbar-thin scrollbar-track-slate-100 scrollbar-thumb-slate-300 scrollbar-track-rounded-md scrollbar-thumb-rounded-md"
              components={componentMap}
            >
              {comment}
            </ReactMarkdown>
          )}
          <div className="p-1">
            <div className="center mb-2 flex flex-col">
              <ReviewRadioBox
                reviewTypes={reviewTypes}
                setChosenReviewType={setChosenReviewType}
                chosenType={chosenReviewType}
              ></ReviewRadioBox>
              <div className="mb-2 flex justify-end">
                <Button
                  id="submit"
                  type="submit"
                  onClick={() => void handleReview()}
                >
                  Review
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
function ReviewRadioBox({
  reviewTypes,
  setChosenReviewType,
  chosenType,
}: {
  reviewTypes: {
    id: ReviewTypes;
    title: string;
    description: string;
    enabled: boolean;
    danger: boolean;
  }[];
  setChosenReviewType: (reviewType: ReviewTypes) => void;
  chosenType: ReviewTypes;
}) {
  return (
    <fieldset>
      <legend className="text-sm font-semibold leading-6 text-slate-900 dark:text-slate-50">
        Review
      </legend>
      <div className="mt-3 space-y-3">
        {reviewTypes.map((reviewType) => (
          <div key={reviewType.id} className="flex items-center">
            <input
              id={reviewType.id}
              name="review-type"
              type="radio"
              defaultChecked={reviewType.id === chosenType}
              className={`h-4 w-4 border-slate-300 focus:ring-indigo-600 disabled:cursor-not-allowed disabled:opacity-50 ${
                reviewType.danger ? "text-red-600" : "text-indigo-600"
              }`}
              onChange={() => setChosenReviewType(reviewType.id)}
              disabled={!reviewType.enabled}
            />
            <span className="ml-3 block">
              <label
                htmlFor={reviewType.id}
                className={`text-sm font-medium leading-6 ${
                  reviewType.enabled
                    ? reviewType.danger
                      ? "text-red-600 dark:text-red-400"
                      : "text-slate-900 dark:text-slate-50"
                    : "text-slate-400 dark:text-slate-500"
                }`}
              >
                {reviewType.title}
              </label>
              <span>
                <p
                  className={`text-xs ${
                    reviewType.enabled
                      ? reviewType.danger
                        ? "text-red-500 dark:text-red-400"
                        : "text-slate-500 dark:text-slate-400"
                      : "text-slate-400 dark:text-slate-500"
                  }`}
                >
                  {reviewType.description}
                </p>
              </span>
            </span>
          </div>
        ))}
      </div>
    </fieldset>
  );
}
export default RequestReview;
