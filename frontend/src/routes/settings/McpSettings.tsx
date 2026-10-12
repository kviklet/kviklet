import { useState } from "react";
import useConfig from "../../components/ConfigProvider";
import McpInstallCommands, {
  CopyableCommand,
} from "../../components/McpInstallCommands";
import Toggle from "../../components/Toggle";
import { EnterpriseFeaturePage } from "../../components/EnterpriseFeature";
import { useHasPermission } from "../../hooks/permissions";
import { mcpServerUrl } from "../../api/McpOAuthApi";

/**
 * How to connect an MCP client, for every user, and the switch for the whole MCP server, for admins.
 */
export default function McpSettings() {
  const { config, updateConfig } = useConfig();
  const canEditConfig = useHasPermission("configuration:edit");
  // The value being saved: the toggle shows it right away and ignores clicks until the save is
  // done. A failed save falls back to the stored value.
  const [saving, setSaving] = useState<boolean | null>(null);

  const toggle = async () => {
    if (!config || saving !== null) return;
    setSaving(!config.mcpEnabled);
    await updateConfig({ mcpEnabled: !config.mcpEnabled });
    setSaving(null);
  };

  if (!config?.licenseValid) {
    return <EnterpriseFeaturePage feature="mcpServer" />;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="mb-2 text-lg">MCP Server</h1>
        <p className="text-sm text-slate-600 dark:text-slate-300">
          Connect Claude Code, Codex or any other MCP client to Kviklet. The
          client logs in with your Kviklet login and acts with your permissions,
          and everything it does lands in the audit log.
        </p>
      </div>

      {canEditConfig && (
        <div className="flex flex-row items-center justify-between rounded-md border border-slate-300 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex flex-col">
            <span className="text-sm font-medium">Enable the MCP server</span>
            <span className="text-xs text-slate-500 dark:text-slate-400">
              Turning it off disconnects every MCP client right away.
            </span>
          </div>
          <Toggle
            active={saving ?? config.mcpEnabled}
            disabled={saving !== null}
            onClick={() => void toggle()}
          />
        </div>
      )}

      {config.mcpEnabled ? (
        <div className="flex flex-col gap-4 rounded-md border border-slate-300 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium text-slate-700 dark:text-slate-200">
              Server URL
            </span>
            <CopyableCommand command={mcpServerUrl} />
            <span className="text-xs text-slate-500 dark:text-slate-400">
              For any other client that supports remote MCP servers with OAuth.
            </span>
          </div>
          <McpInstallCommands />
        </div>
      ) : (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          The MCP server is turned off.
          {!canEditConfig && " Ask an admin to turn it on."}
        </p>
      )}
    </div>
  );
}
