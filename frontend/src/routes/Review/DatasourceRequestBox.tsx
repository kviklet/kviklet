import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  DatasourceExecutionRequestResponseWithComments,
  streamDump,
} from "../../api/ExecutionRequestApi";
import Button from "../../components/Button";
import { timeSince } from "../Requests";
import { AbsoluteInitialBubble as InitialBubble } from "../../components/InitialBubble";
import MenuDropDown from "../../components/MenuDropdown";
import baseUrl from "../../api/base";
import LoadingCancelButton from "../../components/LoadingCancelButton";
import { isRelationalDatabase } from "../../hooks/request";
import Modal from "../../components/Modal";
import SQLDumpConfirm from "../../components/SQLDumpConfirm";
import useNotification from "../../hooks/useNotification";
import { Highlighter } from "./components/Highlighter";

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
      <div className="relative mt-3 flex items-center justify-between">
        {request?.temporaryAccessDuration != null && (
          <AccessDurationInfo duration={request?.temporaryAccessDuration} />
        )}
        <div className="ml-auto flex">
          <MenuDropDown items={menuDropDownItems}></MenuDropDown>
          {isRelationalDatabase(request) ? (
            <LoadingCancelButton
              className=""
              id="runQuery"
              type="submit"
              disabled={request?.reviewStatus !== "APPROVED"}
              onClick={handleButtonClick}
              onCancel={() => void cancelQuery()}
              dataTestId="run-query-button"
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
              dataTestId="run-query-button"
            >
              <div
                className={`play-triangle mr-2 inline-block h-3 w-2 ${
                  (request?.reviewStatus == "APPROVED" && "bg-slate-50") ||
                  "bg-slate-500"
                }`}
              ></div>
              {request?.type == "SingleExecution"
                ? "Run Query"
                : "Start Session"}
            </Button>
          )}
        </div>
        <SQLDumpModal />
      </div>
    </div>
  );
}

function AccessDurationInfo({ duration }: { duration: number }) {
  return (
    <div className="text-sm text-slate-500">
      {duration > 0
        ? `The session will be valid for ${duration} minutes.`
        : "The session will be valid indefinitely."}
    </div>
  );
}

export default DatasourceRequestBox;
