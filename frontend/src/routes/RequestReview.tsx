import { useContext, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import {
  vs,
  duotoneDark,
  materialDark,
} from "react-syntax-highlighter/dist/esm/styles/prism";
import ReactMarkdown from "react-markdown";
import {
  ExecutionRequestResponseWithComments,
  addCommentToRequest,
  addReviewToRequest,
  getSingleRequest,
  runQuery,
  ChangeExecutionRequestPayload,
  patchRequest,
  SelectExecuteResponse,
  ErrorResponse,
  Event,
} from "../api/ExecutionRequestApi";
import Button from "../components/Button";
import { mapStatus, mapStatusToColor, timeSince } from "./Requests";
import { UserResponse } from "../api/UserApi";
import { UserStatusContext } from "../components/UserStatusProvider";
import Table from "../components/Table";
import {
  nightOwl,
  paraisoDark,
  tomorrow,
  tomorrowNight,
  tomorrowNightBlue,
} from "react-syntax-highlighter/dist/esm/styles/hljs";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";

interface RequestReviewParams {
  requestId: string;
}

const Highlighter = (props: { children: string }) => {
  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const style = currentTheme === "dark" ? nightOwl : tomorrow;

  return (
    <SyntaxHighlighter
      showLineNumbers={true}
      style={style}
      language="sql"
      PreTag="div"
      children={String(props.children).replace(/\n$/, "")}
      customStyle={{
        background: "transparent",
      }}
    />
  );
};

const componentMap = {
  code: ({ node, inline, className, children, ...props }: any) => {
    return <Highlighter>{children}</Highlighter>;
  },
  ul: ({ children }: any) => (
    <ul className="list-disc ml-4 mt-4">{children}</ul>
  ),
};

function firstTwoLetters(input: string): string {
  const words = input.split(" ");
  let result = "";

  for (let i = 0; i < words.length; i++) {
    if (result.length < 2) {
      result += words[i][0];
    } else {
      break;
    }
  }

  return result;
}

const useRequest = (id: string) => {
  const [request, setRequest] = useState<
    ExecutionRequestResponseWithComments | undefined
  >(undefined);
  useEffect(() => {
    async function request() {
      const request = await getSingleRequest(id);
      setRequest(request);
    }
    request();
  }, []);

  const [data, setData] = useState<SelectExecuteResponse>();
  const [updatedRows, setUpdatedRows] = useState<number | undefined>(undefined);
  const [executionError, setExecutionError] = useState<
    ErrorResponse | undefined
  >(undefined);

  const addComment = async (comment: string) => {
    const event = await addCommentToRequest(id, comment);
    console.log(event);

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

  const updateRequest = async (request: ChangeExecutionRequestPayload) => {
    const newRequest = await patchRequest(id, request);
    setRequest(newRequest);
  };

  const approve = async (comment: string) => {
    await addReviewToRequest(id, comment, "APPROVE");
    const newRequest = await getSingleRequest(id);
    setRequest(newRequest);
  };

  const execute = async () => {
    const result = await runQuery(id);
    switch (result._type) {
      case "select":
        setData(result);
        break;
      case "update":
        setUpdatedRows(result.rowsUpdated);
        break;
      case "error":
        setExecutionError(result);
        break;
    }
  };

  return {
    request,
    addComment,
    approve,
    execute,
    updateRequest,
    data,
    updatedRows,
    executionError,
  };
};

function RequestReview() {
  const params = useParams() as unknown as RequestReviewParams;
  const {
    request,
    addComment,
    approve,
    execute,
    updateRequest,
    data,
    updatedRows,
    executionError,
  } = useRequest(params.requestId);

  return (
    <div>
      <div className="max-w-3xl m-auto">
        <h1 className="text-3xl my-2 w-full flex">
          <div className="mr-auto">{request?.title}</div>
          <div
            className={`${mapStatusToColor(
              request?.reviewStatus
            )} font-bold rounded-full text-lg ml-auto text-white py-1 px-1.5`}
          >
            {mapStatus(request?.reviewStatus)}
          </div>
        </h1>
        <div className="">
          <div className="">
            <RequestBox
              request={request}
              runQuery={execute}
              updateRequest={updateRequest}
            ></RequestBox>
            {data && <Table data={data}></Table>}
            {updatedRows && (
              <div className="text-slate-500">{updatedRows} rows updated</div>
            )}
            {executionError && (
              <div className="text-red-500">
                {executionError.errorCode}: {executionError.message}
              </div>
            )}
            <div>
              {request === undefined
                ? ""
                : request?.events?.map((event) => (
                    <Comment event={event}></Comment>
                  ))}
              <CommentBox
                addComment={addComment}
                approve={approve}
                userId={request?.author?.id}
              ></CommentBox>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

const InitialBubble = (props: { name?: string | null }) => {
  return (
    <div className="absolute -left-12 rounded-full p-2 bg-slate-900 text-slate-50 border border-slate-700 w-8 h-8 flex items-center justify-center text-l font-bold">
      {firstTwoLetters(props.name ?? "")}
    </div>
  );
};

function RequestBox({
  request,
  runQuery,
  updateRequest,
}: {
  request: ExecutionRequestResponseWithComments | undefined;
  runQuery: () => void;
  updateRequest: (request: { statement?: string }) => Promise<void>;
}) {
  const [editMode, setEditMode] = useState(false);
  const [statement, setStatement] = useState(request?.statement || "");
  const changeStatement = async (e: any) => {
    e.preventDefault();
    await updateRequest({ statement });
    setEditMode(false);
  };
  useEffect(() => {
    setStatement(request?.statement || "");
  }, [request?.statement]);

  const style = localStorage.theme === "dark" ? tomorrowNight : tomorrowNight;

  return (
    <div>
      <div className="relative border-slate-500 dark:bg-slate-950 dark:border dark:border-slate-950">
        <InitialBubble name={request?.author.fullName} />
        <p className="text-slate-800 px-2 py-2 text-sm flex bg-slate-200 dark:bg-slate-950 dark:text-slate-50 dark:border-none border-b border-slate-500 rounded-t-md">
          <div>
            {request?.author?.fullName} wants to execute on:{" "}
            <span className="italic">{request?.connection.displayName}</span>
          </div>
          <div className="ml-auto">
            {timeSince(new Date(request?.createdAt ?? ""))}
          </div>
        </p>
        <div className="p-3">
          <p className="text-slate-500 pb-6">{request?.description}</p>
          {editMode ? (
            <div>
              <textarea
                className="appearance-none block w-full text-gray-700 border border-gray-200 bg-slate-100 focus:bg-white p-1 rounded-md leading-normal mb-2 focus:outline-none focus:border-gray-500"
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
                <Button type="submit" onClick={changeStatement}>
                  Save
                </Button>
              </div>
            </div>
          ) : (
            <div
              className="dark:bg-slate-950 dark:border dark:border-slate-700 rounded dark:hover:border-slate-500 transition-colors"
              onClick={() => setEditMode(true)}
            >
              <Highlighter>
                {request === undefined ? "404" : request.statement}
              </Highlighter>
            </div>
          )}
        </div>
      </div>
      <div className="relative ml-4 flex justify-end">
        <Button
          className="mt-3"
          id="runQuery"
          type={(request?.reviewStatus == "APPROVED" && "submit") || "disabled"}
          onClick={runQuery}
        >
          <div
            className={`play-triangle inline-block w-2 h-3 mr-2 ${
              (request?.reviewStatus == "APPROVED" && "bg-slate-50") ||
              "bg-slate-500"
            }`}
          ></div>
          Run Query
        </Button>
      </div>
      <div className="w-full dark:border-b dark:border-slate-700 mt-3"></div>
    </div>
  );
}

function Comment({ event }: { event: Event }) {
  return (
    <div>
      <div className="relative py-4 ml-4 flex">
        <div className="bg-slate-700 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
          {" "}
        </div>
        <svg className="h-4 w-4 -ml-2 mr-2 mt-0.5 inline-block align-text-bottom items-center bg-slate-900 fill-slate-50 z-0">
          <path d="M11.93 8.5a4.002 4.002 0 0 1-7.86 0H.75a.75.75 0 0 1 0-1.5h3.32a4.002 4.002 0 0 1 7.86 0h3.32a.75.75 0 0 1 0 1.5Zm-1.43-.75a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z"></path>
        </svg>
        <div className="text-slate-500 text-sm">
          {event?.author?.fullName} commented:
        </div>
      </div>
      <div className="relative border-slate-700 rounded-md border">
        <InitialBubble name={event?.author?.fullName} />
        <p className="text-slate-800 dark:text-slate-500 px-2 pt-2 text-sm flex justify-between dark:bg-slate-950 bg-slate-200 border-slate-700 rounded-t-md">
          <div>
            {((event?.createdAt &&
              timeSince(new Date(event.createdAt as any))) as
              | string
              | undefined) || ""}
          </div>
        </p>
        <div className="p-3 dark:bg-slate-950 rounded-md">
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
      <div className="border-slate-300 dark:border-slate-700 rounded-md border relative mb-5">
        <InitialBubble
          name={
            (userContext.userStatus && userContext.userStatus?.fullName) || ""
          }
        />
        <div className="mb-2 border-b-slate-300 border bg-slate-100 dark:bg-slate-900 dark:border-slate-700 rounded-t-md">
          <div className="-mb-px z-10 overflow-auto">
            <button
              className={`mt-2 ml-2 ${
                commentFormVisible
                  ? "border rounded-t-md border-b-white bg-white dark:bg-slate-950 dark:text-slate-50 dark:border-slate-700 dark:border-b-slate-950"
                  : ""
              }  border-slate-300 px-4 py-2 text-sm text-slate-600 leading-6`}
              onClick={() => setCommentFormVisible(true)}
            >
              write
            </button>
            <button
              className={`mt-2 ${
                commentFormVisible
                  ? ""
                  : "border rounded-t-md border-b-white bg-white dark:bg-slate-950 dark:text-slate-50 dark:border-slate-700 dark:border-b-slate-950"
              } border-slate-300  px-4 py-2 text-sm text-slate-600 leading-6`}
              onClick={() => setCommentFormVisible(false)}
            >
              preview
            </button>
          </div>
        </div>
        <div className="px-3">
          {commentFormVisible ? (
            <textarea
              className="appearance-none block w-full text-gray-700 border border-gray-200 bg-slate-100 focus:bg-white p-1 rounded-md leading-normal mb-2 focus:outline-none focus:border-slate-500 dark:bg-slate-900 dark:border-slate-700 dark:text-slate-50 dark:hover:border-slate-600 dark:focus:border-slate-500 dark:focus:hover:border-slate-500 transition-colors"
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
          <div className="px-1">
            <div className="flex justify-end mb-2">
              <Button
                className="mr-2"
                id="addComment"
                onClick={handleAddComment}
              >
                Add Comment
              </Button>
              <Button
                id="approve"
                type={`${isOwnRequest ? "disabled" : "submit"}`}
                onClick={handleApprove}
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
