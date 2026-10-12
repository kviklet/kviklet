import { useState } from "react";
import {
  ChevronDownIcon,
  SparklesIcon,
  XMarkIcon,
} from "@heroicons/react/20/solid";
import useConfig from "./ConfigProvider";
import McpInstallCommands from "./McpInstallCommands";

const dismissedKey = "mcpNudgeDismissed";

const isDismissed = () => {
  try {
    return localStorage.getItem(dismissedKey) === "true";
  } catch {
    return false;
  }
};

/**
 * Tells users they can connect their coding agent to Kviklet, until they close it. Closing it is
 * remembered in this browser; the commands stay on the MCP settings page. Collapsed to one line
 * until the user asks for the commands, so it doesn't push the requests down.
 */
const McpNudge = () => {
  const { config } = useConfig();
  const [dismissed, setDismissed] = useState(isDismissed);
  const [expanded, setExpanded] = useState(false);

  if (dismissed || !config?.licenseValid || !config.mcpEnabled) {
    return null;
  }

  const dismiss = () => {
    try {
      localStorage.setItem(dismissedKey, "true");
    } catch {
      // Without storage it comes back on the next visit, which is all that is lost.
    }
    setDismissed(true);
  };

  return (
    <div
      className="rounded-md border border-slate-200 bg-white px-3 py-2 text-sm dark:border-slate-800 dark:bg-slate-900"
      data-testid="mcp-nudge"
    >
      <div className="flex items-center gap-2">
        <SparklesIcon className="h-4 w-4 shrink-0 text-purple-500 dark:text-purple-400" />
        <span className="text-slate-600 dark:text-slate-300">
          Connect Claude Code or Codex to Kviklet.
        </span>
        <button
          type="button"
          onClick={() => setExpanded(!expanded)}
          className="flex items-center text-blue-600 hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
          aria-expanded={expanded}
          data-testid="mcp-nudge-expand"
        >
          {expanded ? "Hide commands" : "Show commands"}
          <ChevronDownIcon
            className={`h-4 w-4 transition-transform ${
              expanded ? "rotate-180" : ""
            }`}
          />
        </button>
        <button
          type="button"
          onClick={dismiss}
          className="ml-auto rounded p-0.5 text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-200"
          title="Dismiss"
          data-testid="mcp-nudge-dismiss"
        >
          <XMarkIcon className="h-4 w-4" />
        </button>
      </div>
      {expanded && (
        <div className="mb-1 mt-3">
          <McpInstallCommands />
        </div>
      )}
    </div>
  );
};

export default McpNudge;
