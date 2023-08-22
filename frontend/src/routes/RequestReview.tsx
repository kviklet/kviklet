import { useContext, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vs } from "react-syntax-highlighter/dist/esm/styles/prism";
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
import { mapStatusToColor } from "./Requests";
import { UserResponse } from "../api/UserApi";
import { UserStatusContext } from "../components/UserStatusProvider";

interface RequestReviewParams {
  requestId: string;
}

const componentMap = {
  code: ({ node, inline, className, children, ...props }: any) => {
    const match = /language-(\w+)/.exec(className || "");
    return !inline && match ? (
      <SyntaxHighlighter
        style={vs}
        language={match[1]}
        PreTag="div"
        children={String(children).replace(/\n$/, "")}
        {...props}
      />
    ) : (
      <code className={className} {...props}>
        {children}
      </code>
    );
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

const DataTable: React.FC<{ data: SelectExecuteResponse }> = ({ data }) => {
  return (
    <table className="table-auto w-full">
      <thead>
        {data.columns.map((column) => {
          return <th className="px-4 py-2">{column.label}</th>;
        })}
      </thead>
      <tbody>
        {data.data.map((row) => (
          <tr>
            {Object.keys(row).map((cell) => {
              return <td className="border px-4 py-2">{row[cell]}</td>;
            })}
          </tr>
        ))}
      </tbody>
    </table>
  );
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
            {request?.reviewStatus}
          </div>
        </h1>
        <div className="">
          <div className="">
            <RequestBox
              request={request}
              runQuery={execute}
              updateRequest={updateRequest}
            ></RequestBox>
            {data && <DataTable data={data}></DataTable>}
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
              ></CommentBox>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

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

  return (
    <div>
      <div className="relative border-cyan-500 rounded-md border">
        <div className="comment-clip border-cyan-500 bg-cyan-500 w-2 h-4 absolute -left-2 top-2"></div>
        <div className="comment-clip border-cyan-500 bg-cyan-200 w-2 h-4 absolute -left-2 top-2 ml-px"></div>
        <div className="absolute -left-12 rounded-full p-2 bg-cyan-500 text-gray-100  w-8 h-8 flex items-center justify-center text-l font-bold">
          {firstTwoLetters(request?.author?.fullName ?? "")}
        </div>
        <p className="text-slate-800 px-2 py-2 text-sm flex bg-cyan-200 border-b border-cyan-500 rounded-t-md">
          <div>
            {request?.author?.fullName} wants to execute on:{" "}
            <span className="italic">{request?.connection.displayName}</span>
          </div>
          <div className="ml-2">
            Created at: {new Date(request?.createdAt ?? "").toLocaleString()}
          </div>
          <div className="ml-auto">
            {!editMode && (
              <button
                onClick={() => {
                  setEditMode(true);
                }}
              >
                Edit
              </button>
            )}
          </div>
        </p>
        <div className="p-3">
          <p className="text-slate-500">{request?.description}</p>
          {editMode ? (
            <div>
              <textarea
                className="appearance-none block w-full text-gray-700 border border-gray-200 bg-slate-100 focus:bg-white p-1 rounded leading-normal mb-2 focus:outline-none focus:border-gray-500"
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
            <SyntaxHighlighter language="sql" showLineNumbers style={vs}>
              {request === undefined ? "404" : request.statement}
            </SyntaxHighlighter>
          )}
        </div>
      </div>
      <div className="relative ml-4 flex justify-end">
        <div className="bg-slate-500 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
          {" "}
        </div>
        <Button
          className="mt-3"
          id="runQuery"
          type={(request?.reviewStatus == "APPROVED" && "submit") || "disabled"}
          onClick={runQuery}
        >
          <div className="play-triangle inline-block bg-white w-2 h-3 mr-2"></div>
          Run Query
        </Button>
      </div>
    </div>
  );
}

function Comment({ event }: { event: Event }) {
  return (
    <div>
      <div className="relative py-4 ml-4 flex">
        <div className="bg-slate-500 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
          {" "}
        </div>
        <svg className="h-4 w-4 -ml-2 mr-2 mt-0.5 inline-block align-text-bottom items-center bg-white z-0">
          <path d="M11.93 8.5a4.002 4.002 0 0 1-7.86 0H.75a.75.75 0 0 1 0-1.5h3.32a4.002 4.002 0 0 1 7.86 0h3.32a.75.75 0 0 1 0 1.5Zm-1.43-.75a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z"></path>
        </svg>
        <div className="text-slate-500 text-sm">
          {event?.author?.fullName} commented:
        </div>
      </div>
      <div className="relative border-cyan-500 rounded-md border">
        <div className="comment-clip border-cyan-500 bg-cyan-500 w-2 h-4 absolute -left-2 top-2"></div>
        <div className="comment-clip border-cyan-500 bg-cyan-200 w-2 h-4 absolute -left-2 top-2 ml-px"></div>
        <div className="absolute -left-12 rounded-full p-2 bg-cyan-500 text-gray-100  w-8 h-8 flex items-center justify-center text-l font-bold">
          {firstTwoLetters(event?.author?.fullName ?? "")}
        </div>
        <p className="text-slate-800 px-2 py-2 text-sm flex justify-between bg-cyan-200 border-b border-cyan-500 rounded-t-md">
          <div>Created at: {(event?.createdAt ?? "").toLocaleString()}</div>
        </p>
        <div className="p-3">
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
}: {
  addComment: (comment: string) => Promise<void>;
  approve: (comment: string) => Promise<void>;
}) {
  const [commentFormVisible, setCommentFormVisible] = useState<boolean>(true);
  const [comment, setComment] = useState<string>("");

  const userStatus = useContext(UserStatusContext);

  const handleAddComment = async () => {
    await addComment(comment);
    setComment("");
  };

  const handleApprove = async () => {
    await approve(comment);
    setComment("");
  };
  return (
    <div>
      <div className="relative py-4 ml-4">
        <div className="bg-slate-500 w-0.5 absolute block whitespace-pre left-0 top-0 bottom-0">
          {" "}
        </div>
      </div>
      <div className="border-slate-300 rounded-md border relative mb-5">
        <div className="comment-clip border-slate-300 bg-slate-300 w-2 h-4 absolute -left-2 top-2"></div>
        <div className="comment-clip border-slate-300 bg-slate-100 w-2 h-4 absolute -left-2 top-2 ml-px"></div>
        <div className="absolute -left-12 rounded-full p-2 bg-slate-500 text-gray-100  w-8 h-8 flex items-center justify-center text-l font-bold">
          {firstTwoLetters((userStatus && userStatus.fullName) || "")}
        </div>
        <div className="mb-2 border-b-slate-300 border bg-slate-100 rounded-t-md">
          <div className="-mb-px z-10 overflow-auto">
            <button
              className={`mt-2 ml-2 ${
                commentFormVisible
                  ? "border rounded-t-md border-b-white bg-white"
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
                  : "border rounded-t-md border-b-white bg-white"
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
              className="appearance-none block w-full text-gray-700 border border-gray-200 bg-slate-100 focus:bg-white p-1 rounded leading-normal mb-2 focus:outline-none focus:border-gray-500"
              id="comment"
              name="comment"
              rows={4}
              onChange={(event) => setComment(event.target.value)}
              value={comment}
              placeholder="Leave a comment"
            ></textarea>
          ) : (
            <ReactMarkdown
              className="h-28 max-h-48 overflow-y-scroll scrollbar-thin scrollbar-track-slate-100  scrollbar-thumb-slate-300 scrollbar-thumb-rounded scrollbar-track-rounded border-r-slate-300 my-2"
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
              <Button id="approve" type="submit" onClick={handleApprove}>
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
