import { useState } from "react";
import { Link } from "react-router-dom";
import { SparklesIcon, XMarkIcon } from "@heroicons/react/20/solid";
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
 * remembered in this browser; the commands stay on the MCP settings page.
 */
const McpNudge = () => {
  const { config } = useConfig();
  const [dismissed, setDismissed] = useState(isDismissed);

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
      className="relative rounded-md border border-slate-300 bg-white p-4 dark:border-slate-700 dark:bg-slate-900"
      data-testid="mcp-nudge"
    >
      <button
        type="button"
        onClick={dismiss}
        className="absolute right-2 top-2 rounded p-1 text-slate-400 hover:bg-slate-100 hover:text-slate-700 dark:text-slate-500 dark:hover:bg-slate-800 dark:hover:text-slate-200"
        title="Dismiss"
        data-testid="mcp-nudge-dismiss"
      >
        <XMarkIcon className="h-5 w-5" />
      </button>
      <div className="mb-1 flex items-center gap-2 pr-8">
        <SparklesIcon className="h-5 w-5 text-purple-600 dark:text-purple-400" />
        <h2 className="text-sm font-semibold text-slate-900 dark:text-slate-50">
          Use Kviklet from your coding agent
        </h2>
      </div>
      <p className="mb-3 text-sm text-slate-600 dark:text-slate-300">
        Connect Claude Code or Codex to Kviklet&apos;s MCP server. Your agent
        works with your permissions, and its requests go through the same
        reviews as yours.
      </p>
      <McpInstallCommands />
      <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
        You can find these commands later under{" "}
        <Link
          to="/settings/mcp"
          className="text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
        >
          Settings → MCP
        </Link>
        .
      </p>
    </div>
  );
};

export default McpNudge;
