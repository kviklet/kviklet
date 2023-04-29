import { useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { Prism as SyntaxHighlighter } from "react-syntax-highlighter";
import { vs } from "react-syntax-highlighter/dist/esm/styles/prism";
import {
  ExecutionRequestResponse,
  ExecutionRequestResponseWithComments,
  getSingleRequest,
} from "../api/ExecutionRequestApi";
import { z } from "zod";

interface RequestReviewParams {
  requestId: string;
}

const CommentForm = z.object({
  comment: z.string(),
});

const testRequest = {
  id: "test",
  title: "JIRA-123: Test Request",
  description:
    "I want to debug something which is why I join it with something else and order it by something else",
  statement: `Select * from somewhere
where something = 1
  and something_else = 2
  and something_else_else = 3
join something_else_else_else
  on something_else_else_else.id = something_else_else.id
order by something_else_else_else.id`,
  readOnly: true,
  // connection: z.string().min(1), currently not contained in response
  executionStatus: "PENDING",
  createdAt: new Date().toISOString(),
  events: [
    {
      comment: "This is a comment",
      createdAt: new Date().toISOString(),
      id: "test1",
    },
    {
      comment: "This is a comment",
      createdAt: new Date().toISOString(),
      id: "test2",
    },
    {
      comment: "This is a comment",
      createdAt: new Date().toISOString(),
      id: "test3",
    },
  ],
};

function Button(props: {
  id: string;
  onClick: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <button
      id={props.id}
      className={`${props.className} px-4 py-2 font-semibold text-sm bg-sky-500 text-white shadow-sm hover:bg-sky-700 rounded-lg`}
    >
      {props.children}
    </button>
  );
}

function RequestReview() {
  const params = useParams() as unknown as RequestReviewParams;
  const [request, setRequest] = useState<
    ExecutionRequestResponseWithComments | undefined
  >(undefined);
  const loadData = async () => {
    if (params.requestId === "test") {
      setRequest(testRequest);
      return;
    }
    const request = await getSingleRequest(params.requestId);
    setRequest(request);
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleAddComment = async (event: any) => {
    event.preventDefault();
    //if approve button was clicked print help
    //@ts-ignore
    if (event.nativeEvent.submitter.id == "approve") {
      console.log("help");
    }

    const form = event.target as HTMLFormElement;
    const formData = new FormData(form);
    const json = Object.fromEntries(formData.entries());
    const response = await fetch(
      `http://localhost:8080/execution-requests/${params.requestId}/comments/`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ comment: json.comment }),
      }
    );
    console.log(response);
    loadData();
  };

  const runQuery = async () => {
    const response = await fetch(
      `http://localhost:8080/requests/${params.requestId}/run`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({}),
      }
    );
    const json = await response.json();
    console.log(json);
  };
  return (
    <div>
      <div className="max-w-5xl m-auto">
        <h1 className="text-3xl font-bold my-2">{request?.title}</h1>
        <div className="flex">
          <div className="basis-2/3 mr-3">
            <p className="text-slate-500">{request?.description}</p>
            <SyntaxHighlighter language="sql" showLineNumbers style={vs}>
              {request === undefined ? "404" : request.statement}
            </SyntaxHighlighter>
            <Button id="runQuery" onClick={runQuery}>
              Run Query
            </Button>
          </div>
          <div className="basis-1/3 ml-3">
            <div>
              {request === undefined
                ? ""
                : request.events.map((event) => (
                    <div className="shadow-sm m-2 p-2 whitespace-pre-line text-slate-700">
                      {event.comment}
                    </div>
                  ))}
            </div>
            <div>
              <form method="post">
                <textarea
                  className="appearance-none block w-full text-gray-700 border border-gray-200 rounded py-3 px-4 mb-3 leading-tight focus:outline-none focus:border-gray-500"
                  id="comment"
                  name="comment"
                  placeholder="I like it"
                ></textarea>
                <Button
                  className="mr-2"
                  id="addComment"
                  onClick={handleAddComment}
                >
                  Add Comment
                </Button>
                <Button id="approve" onClick={handleAddComment}>
                  Approve
                </Button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default RequestReview;
