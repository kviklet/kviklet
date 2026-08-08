import { useContext, useEffect, useState, MouseEvent } from "react";
import { useNavigate } from "react-router-dom";
import {
  DatasourceExecutionRequestResponseWithComments,
  downloadResults,
  streamDump,
} from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import { timeSince } from "../Requests";
import { AbsoluteInitialBubble as InitialBubble } from "../../components/InitialBubble";
import MenuDropDown from "../../components/MenuDropdown";
import LoadingCancelButton from "../../components/LoadingCancelButton";
import { isRelationalDatabase } from "../../hooks/request";
import Modal from "../../components/Modal";
import SQLDumpConfirm from "../../components/SQLDumpConfirm";
import useNotification from "../../hooks/useNotification";
import { Highlighter } from "./components/Highlighter";
import ApprovalProgress from "./ApprovalProgress";
import { UserStatusContext } from "../../components/UserStatusProvider";
import {
  hasPermission,
  NO_CREATE_PERMISSION_MESSAGE,
  NO_EXECUTE_PERMISSION_MESSAGE,
} from "../../api/Permissions";
import { useHasPermission } from "../../hooks/permissions";

function DatasourceRequestBox({
  request,
  runQuery,
  cancelQuery,
  startServer,
  updateRequest,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  runQuery: (explain?: boolean, dryRun?: boolean) => Promise<void>;
  cancelQuery: () => Promise<void>;
  startServer: () => Promise<void>;
  updateRequest: (request: { statement?: string }) => Promise<void>;
}) {
  const [editMode, setEditMode] = useState(false);
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
    <div className="relative border-slate-500 dark:border dark:border-slate-950 dark:bg-slate-950">
      <InitialBubble name={request?.author.fullName} />
      <div className="flex flex-col gap-4 py-2 sm:flex-row sm:items-stretch">
        {/* Left: request data */}
        <div className="min-w-0 flex-1">
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
                <AccessDurationInfo
                  duration={request?.temporaryAccessDuration}
                />
              </div>
            )}
          </div>
        </div>

        {/* Right: approval status + actions */}
        <div className="flex flex-col items-end justify-between border-slate-200 dark:border-slate-700 sm:border-l sm:pl-4">
          {request && <ApprovalProgress request={request} />}
          <div className="flex">
            <MenuDropDown items={menuDropDownItems}></MenuDropDown>
            {isRelationalDatabase(request) ? (
              <LoadingCancelButton
                className=""
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
                className=""
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
        </div>
      </div>
      <SQLDumpModal />
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
