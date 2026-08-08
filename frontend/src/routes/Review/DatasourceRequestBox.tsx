import { useContext, useEffect, useState, MouseEvent } from "react";
import { DatasourceExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import { timeSince } from "../Requests";
import { AbsoluteInitialBubble as InitialBubble } from "../../components/InitialBubble";
import { Highlighter } from "./components/Highlighter";
import { UserStatusContext } from "../../components/UserStatusProvider";

function DatasourceRequestBox({
  request,
  updateRequest,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  updateRequest: (request: { statement?: string }) => Promise<void>;
}) {
  const [editMode, setEditMode] = useState(false);
  const userContext = useContext(UserStatusContext);
  const isAuthor =
    !!userContext.userStatus &&
    userContext.userStatus.id === request?.author?.id;
  const [statement, setStatement] = useState(request?.statement || "");
  const changeStatement = async (e: MouseEvent<HTMLButtonElement>) => {
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

  return (
    <div className="relative border-slate-500 dark:border dark:border-slate-950 dark:bg-slate-950">
      <InitialBubble name={request?.author.fullName} />
      <div className="py-2">
        <div className="flex text-sm text-slate-800 dark:text-slate-50">
          <div>
            {request?.author?.fullName + questionText}
            <span className="italic">{request?.connection.displayName}</span>
          </div>
          <div
            className="ml-auto dark:text-slate-500"
            title={
              request?.createdAt
                ? new Date(request.createdAt).toLocaleString()
                : undefined
            }
          >
            {timeSince(new Date(request?.createdAt ?? ""))}
          </div>
        </div>
        <div className="py-3">
          <p className="max-w-prose pb-6 text-slate-500">
            {request?.description}
          </p>
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
                    htmlType="reset"
                    onClick={() => {
                      setEditMode(false);
                    }}
                  >
                    Cancel
                  </Button>
                  <Button
                    variant="primary"
                    onClick={(e) => void changeStatement(e)}
                  >
                    Save
                  </Button>
                </div>
              </div>
            ) : (
              <div
                className={
                  "rounded border border-slate-300 transition-colors dark:border-slate-700 dark:bg-slate-950" +
                  (isAuthor
                    ? " cursor-pointer dark:hover:border-slate-500"
                    : "")
                }
                onClick={isAuthor ? () => setEditMode(true) : undefined}
                title={
                  isAuthor
                    ? undefined
                    : "Only the requester can edit the statement"
                }
              >
                <Highlighter>
                  {request === undefined ? "404" : request.statement || ""}
                </Highlighter>
              </div>
            )
          ) : (
            ""
          )}
          {request?.type === "TemporaryAccess" && (
            <div className="pt-3">
              <AccessDurationInfo duration={request?.temporaryAccessDuration} />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function AccessDurationInfo({ duration }: { duration: number | null }) {
  return (
    <div className="text-sm text-slate-500">
      {duration !== null
        ? `The session will be valid for ${duration} minutes.`
        : "The session will be valid indefinitely."}
    </div>
  );
}

export default DatasourceRequestBox;
