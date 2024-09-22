import { useNavigate } from "react-router-dom";

import { KubernetesExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import { timeSince } from "../Requests";
import MenuDropDown from "../../components/MenuDropdown";
import { Highlighter } from "./components/Highlighter";
import { useEffect, useState } from "react";

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

export default KubernetesRequestBox;
