import { KubernetesExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import { timeSince } from "../Requests";
import { AbsoluteInitialBubble as InitialBubble } from "../../components/InitialBubble";
import { Highlighter } from "./components/Highlighter";
import { FC, useContext, useEffect, useState, MouseEvent } from "react";
import { UserStatusContext } from "../../components/UserStatusProvider";

interface KubernetesRequestBoxProps {
  request: KubernetesExecutionRequestResponseWithComments;
  updateRequest: (request: { command?: string }) => Promise<void>;
}

const KubernetesRequestBox: FC<KubernetesRequestBoxProps> = ({
  request,
  updateRequest,
}) => {
  const [editMode, setEditMode] = useState(false);
  const [command, setCommand] = useState(request?.command || "");
  const userContext = useContext(UserStatusContext);
  const isAuthor =
    !!userContext.userStatus &&
    userContext.userStatus.id === request?.author?.id;

  useEffect(() => {
    setCommand(request?.command || "");
  }, [request?.command]);

  const changeCommand = async (e: MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    await updateRequest({ command: command });
    setEditMode(false);
  };

  return (
    <div className="relative border-slate-500 dark:border dark:border-slate-950 dark:bg-slate-950">
      <InitialBubble name={request?.author.fullName} />
      <div className="py-2">
        <div className="text-sm text-slate-800 dark:text-slate-50">
          {request?.author?.fullName}{" "}
          <span
            className="text-slate-500 dark:text-slate-400"
            title={
              request?.createdAt
                ? new Date(request.createdAt).toLocaleString()
                : undefined
            }
          >
            requested {timeSince(new Date(request?.createdAt ?? ""))}
          </span>
        </div>
        <div className="px-4 py-3">
          <p className="max-w-prose pb-6 text-slate-500">
            {request?.description}
          </p>
          <div className="text-slate-500">
            Namespace: <strong>{request?.namespace}</strong>
            <br />
            Pod Name: <strong>{request?.podName}</strong>
            <br />
            Container Name:{" "}
            <strong>{request?.containerName || "Default"}</strong>
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
            <Button
              className="mt-2"
              onClick={() => setEditMode(true)}
              variant={isAuthor ? undefined : "disabled"}
              title={
                isAuthor ? undefined : "Only the requester can edit the command"
              }
            >
              Edit Command
            </Button>
          )}
        </div>
      </div>
    </div>
  );
};

export default KubernetesRequestBox;
