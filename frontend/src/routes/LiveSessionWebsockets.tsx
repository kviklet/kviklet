import ResizableEditor from "./Review/ResizableEditor";
import { defineSessionEditorThemes } from "./Review/sessionEditorThemes";
import { useContext, useEffect, useRef, useState } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {
  CodeBracketIcon,
  PlayIcon,
  ChevronDownIcon,
} from "@heroicons/react/20/solid";
import Button from "../components/Button";
import MultiResult from "../components/MultiResult";
import Spinner from "../components/Spinner";
import SplitButtonDropdown from "../components/SplitButtonDropdown";
import { isRelationalDatabase } from "../hooks/request";
import useLiveSession from "../hooks/useLiveSession";
import useNotification from "../hooks/useNotification";
import ActivityTimeline from "./Review/ActivityTimeline";
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
  const [historyOpen, setHistoryOpen] = useState(false);
  const executingRef = useRef(false);
  const { currentTheme } = useContext(ThemeStatusContext);
  const { userStatus } = useContext(UserStatusContext);
  const { addNotification } = useNotification();
  const isAuthor = !!userStatus && userStatus.id === request.author.id;
  const needsApproval = request.reviewStatus !== "APPROVED";
  const canExecute =
    isAuthor &&
    !expired &&
    !needsApproval &&
    hasPermission(request.permissions, "execution_request:execute");
  const readOnlyReason = expired
    ? "Access has expired. Your query and activity are still available."
    : request.reviewStatus === "REJECTED"
    ? "This request is closed. Execution is disabled."
    : needsApproval
    ? "This session is read-only until the request has been approved."
    : !isAuthor
    ? `You are watching this session. Only ${request.author.fullName} can run statements.`
    : !canExecute
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
      padding: { top: 20, bottom: 20 },
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

  return (
    <section aria-label="Session workspace" aria-busy={!isReady}>
      {readOnlyReason && (
        <WarningBanner className="mb-4" data-testid="read-only-banner">
          {readOnlyReason}
        </WarningBanner>
      )}
      <div className="rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-900">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-t-lg border-b border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-700 dark:bg-slate-800">
          <div className="flex items-center gap-2 text-sm font-medium text-slate-700 dark:text-slate-200">
            <CodeBracketIcon className="h-5 w-5 text-indigo-600 dark:text-indigo-400" />
            Query editor
          </div>
          <div className="flex items-center gap-3">
            {isAuthor && (
              <>
                <span className="hidden text-xs text-slate-500 dark:text-slate-400 sm:inline">
                  {navigator.userAgent.includes("Macintosh") ? "⌘" : "Ctrl"} +
                  Enter
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
            )}
            {!isAuthor && (
              <span className="text-xs text-slate-500 dark:text-slate-400">
                Watching
              </span>
            )}
          </div>
        </div>
        <ResizableEditor>
          <div className="h-full w-full" ref={monacoEl} />
        </ResizableEditor>
        <div className="flex items-center justify-between px-4 py-3 text-sm">
          <span className="font-medium text-slate-700 dark:text-slate-200">
            Results
          </span>
          <span
            role="status"
            className="text-xs text-slate-500 dark:text-slate-400"
          >
            {isLoading
              ? "Running query…"
              : results
              ? `${results.length} ${
                  results.length === 1 ? "result" : "results"
                }`
              : "No query executed yet"}
          </span>
        </div>
        <div
          className="min-h-40 overflow-x-auto px-4 pb-4"
          aria-busy={isLoading}
        >
          {isLoading ? (
            <div className="flex justify-center py-10">
              <Spinner />
            </div>
          ) : results && results.length > 0 ? (
            <MultiResult resultList={results} />
          ) : (
            <div className="py-8 text-center text-sm text-slate-500 dark:text-slate-400">
              {expired
                ? "Review previous executions in Activity below."
                : "Run a query to see its results here."}
            </div>
          )}
        </div>
      </div>
      <div className="mt-5">
        <button
          type="button"
          aria-expanded={historyOpen}
          aria-controls="session-activity"
          onClick={() => setHistoryOpen(!historyOpen)}
          className="flex items-center gap-2 py-2 text-sm font-medium text-slate-600 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-200"
        >
          <ChevronDownIcon
            className={`h-4 w-4 transition-transform ${
              historyOpen ? "" : "-rotate-90"
            }`}
          />
          Activity
        </button>
        <div id="session-activity" hidden={!historyOpen}>
          <ActivityTimeline
            request={request}
            websocketEvents={websocketEvents}
          />
        </div>
      </div>
    </section>
  );
}
