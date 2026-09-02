import React, { useContext, useEffect, useRef, useState } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import Button from "../components/Button";
import MultiResult from "../components/MultiResult";
import Spinner from "../components/Spinner";
import useRequest, { isRelationalDatabase } from "../hooks/request";
import { useParams } from "react-router-dom";
import Breadcrumbs from "../components/Breadcrumbs";
import useLiveSession from "../hooks/useLiveSession";
import useNotification from "../hooks/useNotification";
import ActivityTimeline from "./Review/ActivityTimeline";
import { downloadResults } from "../api/ExecutionRequestApi";
import LoadingCancelButton from "../components/LoadingCancelButton";
import NotAuthorized from "../components/NotAuthorized";
import {
  hasPermission,
  NO_EXECUTE_PERMISSION_MESSAGE,
} from "../api/Permissions";
import { WarningBanner } from "../components/Alert";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";
import { UserStatusContext } from "../components/UserStatusProvider";

interface LiveSessionWebsocketsProps {
  requestId: string;
  initialLanguage: string;
}

interface SessionParams {
  requestId: string;
}

const LiveSessionWebsocketsLoader: React.FC = () => {
  const params = useParams() as unknown as SessionParams;
  const { request, loading } = useRequest(params.requestId);

  if (loading) {
    return <div>Loading...</div>;
  }
  if (!request) {
    // A failed load previously left the page on "Loading..." forever with a
    // misleading "refresh the page" toast.
    return (
      <div className="m-auto mt-10 max-w-3xl">
        <NotAuthorized
          resource="this session"
          message="The request may not exist, or your role has no access to its connection."
        />
      </div>
    );
  }
  return (
    <LiveSessionWebsockets
      requestId={params.requestId}
      initialLanguage={"sql"}
    />
  );
};

const LiveSessionWebsockets: React.FC<LiveSessionWebsocketsProps> = ({
  requestId,
  initialLanguage,
}) => {
  const [editor, setEditor] =
    useState<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoEl = useRef(null);
  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const monacoTheme = currentTheme === "dark" ? "vs-dark" : "vs";

  const { addNotification } = useNotification();
  const updateEditorContent = (newContent: string) => {
    const currentContent = monaco.editor.getModels()[0].getValue();
    if (currentContent !== newContent) {
      monaco.editor.getModels()[0].setValue(newContent);
    }
  };

  const {
    executeQuery,
    updateContent,
    cancelQuery,
    isLoading,
    updatedRows,
    results,
    websocketEvents,
    isSyncing,
  } = useLiveSession(requestId, updateEditorContent);

  const [showSynced, setShowSynced] = useState(false);

  useEffect(() => {
    if (!isSyncing && showSynced) {
      // Just finished syncing, show green briefly then fade
      const timer = setTimeout(() => setShowSynced(false), 1500);
      return () => clearTimeout(timer);
    } else if (isSyncing) {
      setShowSynced(true);
    }
  }, [isSyncing]);

  const { request } = useRequest(requestId);
  const userContext = useContext(UserStatusContext);
  const isAuthor =
    !!request &&
    !!userContext.userStatus &&
    userContext.userStatus.id === request.author?.id;
  // The author may still lack execution_request:execute on this connection; every
  // keystroke would otherwise fire an update_content message that gets rejected.
  const canExecute = hasPermission(
    request?.permissions,
    "execution_request:execute",
  );
  // The backend folds executability (approved, or dry-run enabled) into the execute
  // permission, so pre-approval canExecute is false even for a fully permitted author —
  // the blocker to name then is approval, not their permission.
  const needsApproval = request?.reviewStatus !== "APPROVED";
  const readOnlyReason = canExecute
    ? undefined
    : request?.reviewStatus === "REJECTED"
    ? "Request has been rejected"
    : request?.reviewStatus === "CLOSED"
    ? "Request has been closed"
    : needsApproval
    ? "Request needs to be approved before execution"
    : NO_EXECUTE_PERMISSION_MESSAGE;

  useEffect(() => {
    editor?.updateOptions({ readOnly: !isAuthor || !canExecute });
  }, [editor, isAuthor, canExecute]);

  useEffect(() => {
    if (monacoEl.current) {
      const newEditor = monaco.editor.create(monacoEl.current, {
        value: "",
        language: initialLanguage,
        theme: monacoTheme,
        minimap: { enabled: false },
      });
      setEditor(newEditor);

      const disposable = newEditor.onDidChangeModelContent((e) => {
        if (e.isFlush) {
          // Ignore updates that are not user initiated e.g. our own update call
          return;
        }
        const newContent = newEditor.getValue();
        updateContent(newContent);
      });

      return () => {
        disposable.dispose();
        newEditor.dispose();
      };
    }
  }, [requestId, initialLanguage]);

  useEffect(() => {
    monaco.editor.setTheme(monacoTheme);
  }, [monacoTheme]);

  const onExecuteQueryClick = async (): Promise<void> => {
    const selection = editor?.getSelection();
    const text =
      (selection && editor?.getModel()?.getValueInRange(selection)) ||
      editor?.getValue();
    if (!text) {
      addNotification({
        type: "error",
        title: "Query Error",
        text: "Cannot execute an empty query",
      });
      return;
    }
    await executeQuery(text);
  };

  const handleResultDownload = async () => {
    const selection = editor?.getSelection();
    const query =
      (selection && editor?.getModel()?.getValueInRange(selection)) ||
      editor?.getValue();
    try {
      // Fetch keeps a 403 in-app; a plain link would navigate the tab to raw JSON.
      await downloadResults(requestId, query || "");
    } catch (error) {
      addNotification({
        title: "Failed to download results",
        text:
          error instanceof Error ? error.message : "An unknown error occurred",
        type: "error",
      });
    }
  };

  return (
    <div className="flex h-full flex-col overflow-x-hidden">
      <div className="mx-auto flex h-full w-full max-w-5xl flex-col px-4">
        <Breadcrumbs
          className="mt-5"
          items={[
            { label: "Requests", to: "/requests" },
            { label: request?.title ?? "", to: `/requests/${requestId}` },
            { label: "Live Session" },
          ]}
        />
        {isAuthor && !canExecute && (
          <WarningBanner className="mt-3" data-testid="read-only-banner">
            {needsApproval
              ? "This session is read-only until the request has been approved."
              : "This session is read-only: you lack permission to execute on this connection. Ask an administrator if you think this is a mistake."}
          </WarningBanner>
        )}
        <div className="relative mb-5 mt-3">
          {(isSyncing || showSynced) && (
            <div
              className={`absolute -top-5 right-0 text-xs transition-opacity duration-500 ${
                isSyncing
                  ? "animate-pulse text-gray-500 dark:text-gray-400"
                  : showSynced
                  ? "text-green-500"
                  : "opacity-0"
              }`}
            >
              {isSyncing ? "..." : "✓"}
            </div>
          )}
          <div
            className="h-40 resize-y overflow-auto sm:h-64"
            data-testid="monaco-editor-wrapper"
          >
            <div className="h-full w-full" ref={monacoEl}></div>
          </div>
        </div>
        <div className="mb-4 flex flex-row items-center justify-end gap-2">
          {isAuthor ? (
            <>
              {request?._type === "DATASOURCE" &&
                isRelationalDatabase(request) && (
                  <Button
                    onClick={() => void handleResultDownload()}
                    variant={canExecute ? undefined : "disabled"}
                    title={readOnlyReason}
                  >
                    Execute and Download Results
                  </Button>
                )}
              <LoadingCancelButton
                onClick={onExecuteQueryClick}
                onCancel={() => cancelQuery()}
                variant="primary"
                disabled={!canExecute}
                title={readOnlyReason}
                dataTestId="run-query-button"
              >
                <div className="play-triangle mr-2 inline-block h-3 w-2 bg-slate-50"></div>
                Run Query
              </LoadingCancelButton>
            </>
          ) : (
            <div className="text-sm text-slate-500 dark:text-slate-400">
              You are watching this session — only{" "}
              {request?.author?.fullName || "the requester"} can run statements.
            </div>
          )}
        </div>
        {updatedRows !== undefined && (
          <div className="mb-4 text-green-500">{updatedRows} rows updated</div>
        )}
        <div className="flex h-full justify-center">
          {(isLoading && <Spinner></Spinner>) ||
            (results && <MultiResult resultList={results}></MultiResult>)}
        </div>

        {request && (
          <ActivityTimeline
            request={request}
            websocketEvents={websocketEvents}
          />
        )}
      </div>
    </div>
  );
};

export default LiveSessionWebsocketsLoader;
