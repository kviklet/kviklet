import ResizableEditor from "./Review/ResizableEditor";
import { defineSessionEditorThemes } from "./Review/sessionEditorThemes";
import { useContext, useEffect, useRef, useState } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import { PlayIcon } from "@heroicons/react/20/solid";
import Button from "../components/Button";
import MultiResult from "../components/MultiResult";
import Spinner from "../components/Spinner";
import SplitButtonDropdown from "../components/SplitButtonDropdown";
import { isRelationalDatabase } from "../hooks/request";
import useLiveSession from "../hooks/useLiveSession";
import useNotification from "../hooks/useNotification";
import {
  downloadResults,
  Execute,
  ExecutionRequestResponseWithComments,
} from "../api/ExecutionRequestApi";
import {
  hasPermission,
  NO_EXECUTE_PERMISSION_MESSAGE,
} from "../api/Permissions";
import { WarningBanner } from "../components/Alert";
import { ThemeStatusContext } from "../components/ThemeStatusProvider";
import { UserStatusContext } from "../components/UserStatusProvider";

// The editor sits where a query request shows its statement, so it borrows that
// box's chrome; the request sidebar owns status and expiry, so nothing here
// repeats them beyond the disabled button's tooltip.
export default function LiveSessionWebsockets({
  request,
  expired,
  onEvents,
  onRefresh,
}: {
  request: ExecutionRequestResponseWithComments;
  expired: boolean;
  onEvents: (events: Execute[]) => void;
  onRefresh: () => Promise<void>;
}) {
  const requestId = request.id;
  const monacoEl = useRef<HTMLDivElement>(null);
  const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
  const pendingContent = useRef("");
  const [editor, setEditor] =
    useState<monaco.editor.IStandaloneCodeEditor | null>(null);
  const [hasSelection, setHasSelection] = useState(false);
  const [hasContent, setHasContent] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const executingRef = useRef(false);
  const { currentTheme } = useContext(ThemeStatusContext);
  const { userStatus } = useContext(UserStatusContext);
  const { addNotification } = useNotification();
  const isAuthor = !!userStatus && userStatus.id === request.author.id;
  const needsApproval = request.reviewStatus !== "APPROVED";
  const hasExecutePermission = hasPermission(
    request.permissions,
    "execution_request:execute",
  );
  const canExecute =
    isAuthor && !expired && !needsApproval && hasExecutePermission;
  const readOnlyReason = expired
    ? "Access has expired"
    : needsApproval
    ? "Request needs to be approved before execution"
    : !isAuthor
    ? `Only ${request.author.fullName} can run statements`
    : !hasExecutePermission
    ? NO_EXECUTE_PERMISSION_MESSAGE
    : undefined;
  const {
    executeQuery,
    updateContent,
    cancelQuery,
    isLoading,
    isReady,
    results,
    websocketEvents,
  } = useLiveSession(requestId, (content) => {
    pendingContent.current = content;
    const model = editorRef.current?.getModel();
    if (model && model.getValue() !== content) model.setValue(content);
  });
  const canEdit = canExecute && isReady;
  const permittedRef = useRef(canEdit);
  permittedRef.current = canEdit;
  const updateRef = useRef(updateContent);
  updateRef.current = updateContent;

  useEffect(() => {
    onEvents(websocketEvents);
  }, [websocketEvents, onEvents]);
  useEffect(() => {
    editor?.updateOptions({ readOnly: !canEdit });
  }, [editor, canEdit]);

  useEffect(() => {
    defineSessionEditorThemes();
    if (!monacoEl.current) return;
    const instance = monaco.editor.create(monacoEl.current, {
      value: pendingContent.current,
      language:
        request._type === "DATASOURCE" && !isRelationalDatabase(request)
          ? "javascript"
          : "sql",
      theme: `kviklet-${currentTheme}`,
      automaticLayout: true,
      readOnly: !permittedRef.current,
      minimap: { enabled: false },
      fontFamily: "'SFMono-Regular', Consolas, 'Liberation Mono', monospace",
      fontSize: 14,
      lineHeight: 24,
      padding: { top: 12, bottom: 12 },
      lineNumbersMinChars: 3,
      glyphMargin: false,
      folding: false,
      scrollBeyondLastLine: false,
      renderLineHighlight: "line",
      overviewRulerLanes: 0,
      hideCursorInOverviewRuler: true,
      scrollbar: {
        verticalScrollbarSize: 8,
        horizontalScrollbarSize: 8,
        useShadows: false,
      },
      ariaLabel: "Session query editor",
    });
    editorRef.current = instance;
    setEditor(instance);
    setHasContent(!!instance.getValue().trim());
    const contentListener = instance.onDidChangeModelContent((event) => {
      setHasContent(!!instance.getValue().trim());
      if (!event.isFlush && permittedRef.current)
        updateRef.current(instance.getValue());
    });
    const selectionListener = instance.onDidChangeCursorSelection((event) =>
      setHasSelection(!event.selection.isEmpty()),
    );
    return () => {
      contentListener.dispose();
      selectionListener.dispose();
      editorRef.current = null;
      instance.dispose();
    };
  }, [requestId]);

  useEffect(() => {
    editor?.updateOptions({ theme: `kviklet-${currentTheme}` });
  }, [editor, currentTheme]);

  const selectedQuery = () => {
    const instance = editorRef.current;
    const selection = instance?.getSelection();
    return (
      (selection && instance?.getModel()?.getValueInRange(selection)) ||
      instance?.getValue() ||
      ""
    );
  };
  const run = async (download = false) => {
    if (!canEdit || isLoading || downloading || executingRef.current) return;
    const query = selectedQuery();
    if (!query.trim()) return;
    executingRef.current = true;
    try {
      updateContent.flush();
      if (download) {
        setDownloading(true);
        await downloadResults(requestId, query);
      } else {
        await executeQuery(query);
      }
      await onRefresh();
    } catch (error) {
      addNotification({
        title: "Failed to download results",
        text:
          error instanceof Error ? error.message : "An unknown error occurred",
        type: "error",
      });
    } finally {
      executingRef.current = false;
      setDownloading(false);
    }
  };
  const runRef = useRef(run);
  runRef.current = run;
  useEffect(() => {
    const action = editor?.addAction({
      id: "kviklet.run-query",
      label: "Run query or selection",
      keybindings: [monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter],
      run: () => {
        void runRef.current();
      },
    });
    return () => action?.dispose();
  }, [editor]);
  const busy = isLoading || downloading;
  const downloadPossible =
    request._type === "DATASOURCE" && isRelationalDatabase(request);
  // Expiry and approval are visible in the sidebar; only a missing permission
  // needs spelling out, since nothing else on the page explains it.
  const showPermissionBanner =
    isAuthor && !expired && !needsApproval && !hasExecutePermission;

  return (
    <section aria-label="Session workspace" aria-busy={!isReady}>
      {showPermissionBanner && (
        <WarningBanner className="mb-3" data-testid="read-only-banner">
          {NO_EXECUTE_PERMISSION_MESSAGE}
        </WarningBanner>
      )}
      <div className="overflow-hidden rounded border border-slate-300 dark:border-slate-700">
        <ResizableEditor>
          <div className="h-full w-full" ref={monacoEl} />
        </ResizableEditor>
      </div>
      <div className="mt-3 flex flex-wrap items-center justify-end gap-3">
        {isAuthor ? (
          <>
            <span className="hidden text-xs text-slate-500 dark:text-slate-400 sm:inline">
              {navigator.userAgent.includes("Macintosh") ? "⌘" : "Ctrl"} + Enter
            </span>
            <div className="flex">
              <Button
                className={downloadPossible ? "rounded-r-none" : ""}
                variant={
                  isLoading || (canEdit && hasContent && !downloading)
                    ? "primary"
                    : "disabled"
                }
                onClick={() => (isLoading ? cancelQuery() : void run())}
                title={readOnlyReason}
                dataTestId="run-query-button"
              >
                <span className="flex items-center gap-2">
                  <PlayIcon className="h-4 w-4" />
                  {isLoading
                    ? "Cancel query"
                    : downloading
                    ? "Downloading…"
                    : hasSelection
                    ? "Run selection"
                    : "Run query"}
                </span>
              </Button>
              {downloadPossible && (
                <SplitButtonDropdown
                  variant={canExecute && !busy ? "primary" : "disabled"}
                  items={[
                    {
                      content: "Run and download…",
                      description:
                        "Execute the query or selection and save its results.",
                      enabled: canEdit && hasContent && !busy,
                      tooltip: readOnlyReason,
                      onClick: () => void run(true),
                    },
                  ]}
                />
              )}
            </div>
          </>
        ) : (
          <span className="text-sm text-slate-500 dark:text-slate-400">
            You are watching this session. {readOnlyReason}.
          </span>
        )}
      </div>
      <div className="mt-4 flex justify-center" aria-busy={isLoading}>
        {isLoading ? (
          <Spinner />
        ) : results && results.length > 0 ? (
          <MultiResult resultList={results} />
        ) : null}
      </div>
    </section>
  );
}
