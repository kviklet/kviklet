import { useState } from "react";
import useRoleSyncConfig from "../../hooks/roleSyncConfig";
import { useRoles } from "./RolesSettings";
import Spinner from "../../components/Spinner";
import Toggle from "../../components/Toggle";
import InputField from "../../components/InputField";
import Button from "../../components/Button";
import { TrashIcon } from "@heroicons/react/20/solid";
import useConfig from "../../hooks/config";

export default function RoleSyncSettings() {
  const { config, loading, updateConfig, addMapping, deleteMapping } =
    useRoleSyncConfig();
  const { roles, isLoading: rolesLoading } = useRoles();
  const { config: appConfig } = useConfig();

  const [idpGroupName, setIdpGroupName] = useState("");
  const [selectedRoleId, setSelectedRoleId] = useState("");

  if (loading || rolesLoading) {
    return <Spinner />;
  }

  if (!config) {
    return (
      <div className="text-slate-500 dark:text-slate-400">
        Failed to load role sync configuration
      </div>
    );
  }

  const handleToggleEnabled = async () => {
    await updateConfig({
      enabled: !config.enabled,
      syncMode: config.syncMode,
      groupsAttribute: config.groupsAttribute,
    });
  };

  const handleSyncModeChange = async (
    event: React.ChangeEvent<HTMLSelectElement>,
  ) => {
    await updateConfig({
      enabled: config.enabled,
      syncMode: event.target.value as
        | "FULL_SYNC"
        | "ADDITIVE"
        | "FIRST_LOGIN_ONLY",
      groupsAttribute: config.groupsAttribute,
    });
  };

  const handleGroupsAttributeChange = async (
    event: React.FocusEvent<HTMLInputElement>,
  ) => {
    const newValue = event.target.value.trim();
    if (newValue && newValue !== config.groupsAttribute) {
      await updateConfig({
        enabled: config.enabled,
        syncMode: config.syncMode,
        groupsAttribute: newValue,
      });
    }
  };

  const handleAddMapping = async () => {
    if (idpGroupName.trim() && selectedRoleId) {
      await addMapping({
        idpGroupName: idpGroupName.trim(),
        roleId: selectedRoleId,
      });
      setIdpGroupName("");
      setSelectedRoleId("");
    }
  };

  const handleDeleteMapping = async (id: string) => {
    await deleteMapping(id);
  };

  const syncModeDescriptions = {
    FULL_SYNC:
      "Roles are fully managed by IdP groups. User roles exactly match their IdP group mappings.",
    ADDITIVE:
      "IdP groups add roles but don't remove existing roles. Best for giving additional access.",
    FIRST_LOGIN_ONLY:
      "Roles are only set from IdP groups on first login. Manual changes are preserved.",
  };

  return (
    <div className="mx-auto max-w-7xl">
      <h1 className="mb-6 text-lg font-semibold text-slate-900 dark:text-slate-50">
        Role Sync Configuration
      </h1>

      <div className="space-y-6">
        {/* Enable/Disable Toggle */}
        <div className="rounded-lg bg-white p-6 shadow dark:border dark:border-slate-800 dark:bg-slate-900">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-sm font-medium text-slate-900 dark:text-slate-50">
                Enable Role Sync
              </h3>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                Automatically sync user roles from identity provider groups
              </p>
            </div>
            <Toggle
              active={config.enabled}
              onClick={() => void handleToggleEnabled()}
            />
          </div>
        </div>

        {/* Sync Mode Selection */}
        <div className="rounded-lg bg-white p-6 shadow dark:border dark:border-slate-800 dark:bg-slate-900">
          <label
            htmlFor="sync-mode"
            className="block text-sm font-medium text-slate-900 dark:text-slate-50"
          >
            Sync Mode
          </label>
          <select
            id="sync-mode"
            name="sync-mode"
            value={config.syncMode}
            onChange={(e) => void handleSyncModeChange(e)}
            disabled={!config.enabled}
            className="mt-2 block w-full rounded-md border-0 py-2 pl-3 pr-10 text-slate-900 ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-indigo-600 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-500 dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 dark:disabled:bg-slate-800 dark:disabled:text-slate-600 sm:text-sm sm:leading-6"
          >
            <option value="FULL_SYNC">Full Sync</option>
            <option value="ADDITIVE">Additive</option>
            <option value="FIRST_LOGIN_ONLY">First Login Only</option>
          </select>
          <p className="mt-2 text-sm text-slate-500 dark:text-slate-400">
            {syncModeDescriptions[config.syncMode]}
          </p>
        </div>

        {/* Groups Attribute */}
        <div className="rounded-lg bg-white p-6 shadow dark:border dark:border-slate-800 dark:bg-slate-900">
          <InputField
            label="Groups Attribute Name"
            type="text"
            id="groups-attribute"
            value={config.groupsAttribute}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              updateConfig({
                enabled: config.enabled,
                syncMode: config.syncMode,
                groupsAttribute: (e.target as HTMLInputElement).value,
              })
            }
            onBlur={handleGroupsAttributeChange}
            disabled={!config.enabled}
            placeholder="groups"
            stacked
            tooltip="The name of the attribute in your IdP that contains the user's group memberships"
          />
          {appConfig?.ldapEnabled && (
            <p className="mt-2 text-sm text-amber-600 dark:text-amber-400">
              Note: For LDAP authentication, this setting is ignored. Groups are
              always read from the{" "}
              <code className="rounded bg-amber-100 px-1 dark:bg-amber-900/30">
                memberOf
              </code>{" "}
              attribute.
            </p>
          )}
        </div>

        {/* Role Mappings Table */}
        <div className="rounded-lg bg-white p-6 shadow dark:border dark:border-slate-800 dark:bg-slate-900">
          <h3 className="mb-4 text-sm font-medium text-slate-900 dark:text-slate-50">
            Role Mappings
          </h3>

          {config.mappings.length > 0 ? (
            <div className="overflow-hidden rounded-md border border-slate-200 dark:border-slate-700">
              <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-700">
                <thead className="bg-slate-50 dark:bg-slate-800">
                  <tr>
                    <th
                      scope="col"
                      className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400"
                    >
                      IdP Group Name
                    </th>
                    <th
                      scope="col"
                      className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400"
                    >
                      Kviklet Role
                    </th>
                    <th scope="col" className="relative px-6 py-3">
                      <span className="sr-only">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 bg-white dark:divide-slate-700 dark:bg-slate-900">
                  {config.mappings.map((mapping) => (
                    <tr key={mapping.id}>
                      <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-900 dark:text-slate-100">
                        {mapping.idpGroupName}
                      </td>
                      <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-900 dark:text-slate-100">
                        {mapping.roleName}
                      </td>
                      <td className="whitespace-nowrap px-6 py-4 text-right text-sm font-medium">
                        <button
                          onClick={() => void handleDeleteMapping(mapping.id)}
                          className="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300"
                          title="Delete mapping"
                        >
                          <TrashIcon className="h-5 w-5" />
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="mb-4 text-sm text-slate-500 dark:text-slate-400">
              No role mappings configured. Add a mapping below to get started.
            </p>
          )}

          {/* Add Mapping Form */}
          <div className="mt-6 space-y-4">
            <h4 className="text-sm font-medium text-slate-900 dark:text-slate-50">
              Add New Mapping
            </h4>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <InputField
                label="IdP Group Name"
                type="text"
                id="idp-group-name"
                value={idpGroupName}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                  setIdpGroupName((e.target as HTMLInputElement).value)
                }
                placeholder="e.g., engineering, admins"
                stacked
              />
              <div className="flex flex-col">
                <label
                  htmlFor="role-select"
                  className="block text-sm font-medium text-slate-700 dark:text-slate-200"
                >
                  Kviklet Role
                </label>
                <select
                  id="role-select"
                  name="role-select"
                  value={selectedRoleId}
                  onChange={(e) => setSelectedRoleId(e.target.value)}
                  className="mt-2 block w-full rounded-md border-0 py-2 pl-3 pr-10 text-slate-900 ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-indigo-600 dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 sm:text-sm sm:leading-6"
                >
                  <option value="">Select a role...</option>
                  {roles.map((role) => (
                    <option key={role.id} value={role.id}>
                      {role.name}
                    </option>
                  ))}
                </select>
              </div>
            </div>
            <div className="flex justify-end">
              <Button
                variant={
                  idpGroupName.trim() && selectedRoleId ? "primary" : "disabled"
                }
                onClick={() => void handleAddMapping()}
              >
                Add Mapping
              </Button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
