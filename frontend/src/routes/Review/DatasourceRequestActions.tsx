import { useContext, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  DatasourceExecutionRequestResponseWithComments,
  downloadResults,
  streamDump,
} from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import MenuDropDown from "../../components/MenuDropdown";
import LoadingCancelButton from "../../components/LoadingCancelButton";
import { isRelationalDatabase } from "../../hooks/request";
import Modal from "../../components/Modal";
import SQLDumpConfirm from "../../components/SQLDumpConfirm";
import useNotification from "../../hooks/useNotification";
import { UserStatusContext } from "../../components/UserStatusProvider";
import {
  hasPermission,
  NO_CREATE_PERMISSION_MESSAGE,
  NO_EXECUTE_PERMISSION_MESSAGE,
} from "../../api/Permissions";
import { useHasPermission } from "../../hooks/permissions";

function DatasourceRequestActions({
  request,
  runQuery,
  cancelQuery,
  startServer,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  runQuery: (explain?: boolean, dryRun?: boolean) => Promise<void>;
  cancelQuery: () => Promise<void>;
  startServer: () => Promise<void>;
}) {
  const { addNotification } = useNotification();
  const userContext = useContext(UserStatusContext);
  const isAuthor =
    !!userContext.userStatus &&
    userContext.userStatus.id === request?.author?.id;
  // Resolved by the backend against this request: policy vote on the connection plus
  // authorship/executability. Absent also while the request is unapproved, so approval
  // reasons are checked first wherever this feeds a tooltip.
  const canExecute = hasPermission(
    request?.permissions,
    "execution_request:execute",
  );
  // Copying opens the new-request form, where the connection can still be changed —
  // so this is the global "can create anywhere" check, not one on this connection.
  const canCreateRequests = useHasPermission("execution_request:edit");
  const [showSQLDumpModal, setShowSQLDumpModal] = useState(false);
  const navigate = useNavigate();

  const navigateCopy = () => {
    void navigate(`/new`, {
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

  const getDisabledReason = () => {
    if (request?.reviewStatus !== "APPROVED") {
      return "Request needs to be approved before execution";
    } else if (request?.executionStatus === "EXECUTED") {
      return "Request has already been executed";
    } else if (request?.type === "Dump" && !isAuthor) {
      return "Only the requester can download the dump";
    } else if (executesDirectly && !canExecute) {
      return NO_EXECUTE_PERMISSION_MESSAGE;
    }
    return undefined;
  };

  // Start/Watch Session only navigates to the live session; the execute permission is
  // enforced there. Run Query and Get SQL Dump execute right here.
  const executesDirectly =
    request?.type === "SingleExecution" || request?.type === "Dump";

  // Downloading executes the stored statement, so it's only available for relational
  // (non-Mongo) single-execution requests. Temporary access downloads run from the live
  // session, which sends the editor's query along.
  const downloadPossible =
    isRelationalDatabase(request) && request?.type === "SingleExecution";
  const downloadEnabled =
    downloadPossible &&
    canExecute &&
    request?.reviewStatus === "APPROVED" &&
    request?.executionStatus !== "EXECUTED";
  const handleDownloadResults = async () => {
    if (!request) {
      return;
    }
    try {
      await downloadResults(request.id);
    } catch (error) {
      addNotification({
        title: "Failed to download results",
        text:
          error instanceof Error ? error.message : "An unknown error occurred",
        type: "error",
      });
    }
  };
  const menuDropDownItems = [
    {
      onClick: () => {
        void handleDownloadResults();
      },
      enabled: downloadEnabled,
      tooltip: downloadPossible ? getDisabledReason() : undefined,
      content: "Execute and Download Results",
    },
    {
      onClick: () => {
        void navigateCopy();
      },
      enabled: canCreateRequests,
      tooltip: canCreateRequests ? undefined : NO_CREATE_PERMISSION_MESSAGE,
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
              request?.connection?.explainEnabled &&
              canExecute &&
              (request?.reviewStatus === "APPROVED" ||
                request?.reviewStatus === "AWAITING_APPROVAL"),
            tooltip:
              // Pre-approval explain is only executable on dry-run-enabled
              // connections (backend isExecutable()), so anywhere else the
              // blocker is approval, not the user's permission.
              request?.reviewStatus !== "APPROVED" &&
              !(
                request?.reviewStatus === "AWAITING_APPROVAL" &&
                request?.connection?.dryRunEnabled
              )
                ? "Request needs approval before explain"
                : !canExecute
                ? NO_EXECUTE_PERMISSION_MESSAGE
                : undefined,
            content: "Explain",
          },
        ]
      : []),
    ...(request?.type == "SingleExecution" && request?.connection?.dryRunEnabled
      ? [
          {
            onClick: () => {
              void runQuery(false, true);
            },
            enabled:
              canExecute &&
              (request?.connection?.dryRunRequiresApproval === false ||
                request?.reviewStatus === "APPROVED"),
            tooltip:
              request?.connection?.dryRunRequiresApproval === true &&
              request?.reviewStatus !== "APPROVED"
                ? "Request needs approval before dry run"
                : !canExecute
                ? NO_EXECUTE_PERMISSION_MESSAGE
                : undefined,
            content: "Dry Run",
          },
        ]
      : []),
    ...(request?.type == "TemporaryAccess"
      ? [
          {
            onClick: () => {
              void startServer();
            },
            enabled:
              isAuthor && canExecute && request?.reviewStatus === "APPROVED",
            tooltip: !isAuthor
              ? "Proxy access is granted only to the requester"
              : request?.reviewStatus !== "APPROVED"
              ? "Request needs to be approved before starting the proxy"
              : !canExecute
              ? NO_EXECUTE_PERMISSION_MESSAGE
              : undefined,
            content: "Start Proxy",
          },
        ]
      : []),
  ];

  const fileHandler = async (connectionId: string) => {
    try {
      // Create a handle for the file the user wants to save
      const fileHandle: FileSystemFileHandle = await window.showSaveFilePicker({
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
  const handleStreamSQLDump = async (
    executionRequestId: string,
    connectionId: string,
  ) => {
    try {
      const fileHandle = await fileHandler(connectionId);
      const responseStream = await streamDump(executionRequestId);

      const reader = responseStream.getReader();
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
    if (!showSQLDumpModal || !request) return null;
    return (
      <Modal setVisible={setShowSQLDumpModal}>
        <SQLDumpConfirm
          title="Get SQL Dump"
          message={`Are you sure you want to get sql dump from database ${request?.connection?.displayName}?`}
          onConfirm={() =>
            handleStreamSQLDump(request.id, request.connection.id)
          }
          onCancel={() => setShowSQLDumpModal(false)}
        />
      </Modal>
    );
  };

  const handleButtonClick = async () => {
    if (request?.type === "Dump") {
      setShowSQLDumpModal(true);
    } else {
      await runQuery();
    }
  };

  return (
    <>
      <div className="flex w-full">
        <MenuDropDown items={menuDropDownItems}></MenuDropDown>
        {isRelationalDatabase(request) ? (
          <LoadingCancelButton
            className="flex-1"
            id="runQuery"
            variant="primary"
            disabled={
              request?.reviewStatus !== "APPROVED" ||
              request?.executionStatus === "EXECUTED" ||
              (request?.type === "Dump" && !isAuthor) ||
              (executesDirectly && !canExecute)
            }
            onClick={handleButtonClick}
            onCancel={() => void cancelQuery()}
            dataTestId="run-query-button"
            title={getDisabledReason()}
          >
            {request?.type === "SingleExecution"
              ? "Run Query"
              : request?.type === "TemporaryAccess"
              ? isAuthor
                ? "Start Session"
                : "Watch Session"
              : "Get SQL Dump"}
          </LoadingCancelButton>
        ) : (
          <Button
            className="flex-1"
            id="runQuery"
            variant={
              (request?.reviewStatus == "APPROVED" &&
                request?.executionStatus !== "EXECUTED" &&
                !(executesDirectly && !canExecute) &&
                "primary") ||
              "disabled"
            }
            onClick={() => void runQuery()}
            dataTestId="run-query-button"
            title={getDisabledReason()}
          >
            {request?.type == "SingleExecution"
              ? "Run Query"
              : isAuthor
              ? "Start Session"
              : "Watch Session"}
          </Button>
        )}
      </div>
      <SQLDumpModal />
    </>
  );
}

export default DatasourceRequestActions;
