import { FC, useContext } from "react";
import { useNavigate } from "react-router-dom";
import { KubernetesExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import MenuDropDown from "../../components/MenuDropdown";
import ApprovalProgress from "./ApprovalProgress";
import { UserStatusContext } from "../../components/UserStatusProvider";
import {
  hasPermission,
  NO_CREATE_PERMISSION_MESSAGE,
  NO_EXECUTE_PERMISSION_MESSAGE,
} from "../../api/Permissions";
import { useHasPermission } from "../../hooks/permissions";

interface KubernetesRequestSidebarProps {
  request: KubernetesExecutionRequestResponseWithComments;
  runQuery: (explain?: boolean) => Promise<void>;
}

const KubernetesRequestSidebar: FC<KubernetesRequestSidebarProps> = ({
  request,
  runQuery,
}) => {
  const userContext = useContext(UserStatusContext);
  const isAuthor =
    !!userContext.userStatus &&
    userContext.userStatus.id === request?.author?.id;
  const canExecute = hasPermission(
    request?.permissions,
    "execution_request:execute",
  );
  // Copying opens the new-request form, where the connection can still be changed —
  // so this is the global "can create anywhere" check, not one on this connection.
  const canCreateRequests = useHasPermission("execution_request:edit");
  const executesDirectly = request?.type === "SingleExecution";

  const getDisabledReason = () => {
    if (request?.reviewStatus !== "APPROVED") {
      return "Request needs to be approved before execution";
    } else if (executesDirectly && !canExecute) {
      return NO_EXECUTE_PERMISSION_MESSAGE;
    }
    return undefined;
  };

  const navigate = useNavigate();

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
      enabled: canCreateRequests,
      tooltip: canCreateRequests ? undefined : NO_CREATE_PERMISSION_MESSAGE,
      content: "Copy Request",
    },
  ];

  return (
    <div className="flex items-start justify-between gap-4 md:flex-col md:items-stretch">
      <ApprovalProgress request={request} />
      <div className="flex md:w-full">
        <MenuDropDown items={menuDropDownItems}></MenuDropDown>
        <Button
          className="flex-1"
          id="runQuery"
          variant={
            (request?.reviewStatus == "APPROVED" &&
              !(executesDirectly && !canExecute) &&
              "primary") ||
            "disabled"
          }
          title={getDisabledReason()}
          onClick={() => void runQuery(false)}
        >
          {request?.type == "SingleExecution"
            ? "Run Command"
            : isAuthor
            ? "Start Session"
            : "Watch Session"}
        </Button>
      </div>
    </div>
  );
};

export default KubernetesRequestSidebar;
