import { useState } from "react";
import { CheckIcon, ClipboardIcon } from "@heroicons/react/20/solid";
import { mcpServerUrl } from "../api/McpOAuthApi";
import useNotification from "../hooks/useNotification";

const clients = [
  {
    name: "Claude Code",
    command: `claude mcp add --transport http kviklet ${mcpServerUrl}`,
    hint: "Then run /mcp in Claude Code and log in to Kviklet.",
  },
  {
    name: "Codex",
    command: `codex mcp add kviklet --url ${mcpServerUrl}`,
    hint: "Then run codex mcp login kviklet if Codex doesn't open the login itself.",
  },
];

const CopyableCommand = ({ command }: { command: string }) => {
  const { addNotification } = useNotification();
  const [copied, setCopied] = useState(false);

  const copy = () => {
    navigator.clipboard.writeText(command).then(
      () => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      },
      () => {
        addNotification({
          title: "Failed to copy to clipboard",
          text: "You can select and copy the command manually instead.",
          type: "error",
        });
      },
    );
  };

  return (
    <div className="flex items-center gap-2">
      <code className="min-w-0 flex-1 overflow-x-auto whitespace-nowrap rounded border border-slate-300 bg-white px-2 py-1.5 font-mono text-xs text-slate-800 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200">
        {command}
      </code>
      <button
        type="button"
        onClick={copy}
        className="rounded p-1.5 text-slate-500 hover:bg-slate-100 hover:text-slate-800 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-100"
        title="Copy to clipboard"
      >
        {copied ? (
          <CheckIcon className="h-4 w-4" />
        ) : (
          <ClipboardIcon className="h-4 w-4" />
        )}
      </button>
    </div>
  );
};

/** The commands that connect the common MCP clients to this Kviklet instance. */
const McpInstallCommands = () => (
  <div className="flex flex-col gap-3">
    {clients.map((client) => (
      <div key={client.name} className="flex flex-col gap-1">
        <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
          {client.name}
        </span>
        <CopyableCommand command={client.command} />
        <span className="text-xs text-slate-500 dark:text-slate-400">
          {client.hint}
        </span>
      </div>
    ))}
  </div>
);

export { CopyableCommand };
export default McpInstallCommands;
