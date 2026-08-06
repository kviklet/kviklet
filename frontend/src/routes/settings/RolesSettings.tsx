import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { RoleResponse, getRoles, removeRole } from "../../api/RoleApi";
import { ConfigResponse } from "../../api/ConfigApi";
import React from "react";
import { useNavigate } from "react-router-dom";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import useConfig from "../../components/ConfigProvider";
import SettingsTable, { Column } from "../../components/SettingsTable";
import Button from "../../components/Button";
import {
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
} from "@headlessui/react";
import { CheckIcon, ChevronUpDownIcon } from "@heroicons/react/20/solid";

const useRoles = (): {
  roles: RoleResponse[];
  isLoading: boolean;
  error: Error | null;
  deleteRole: (id: string) => Promise<void>;
} => {
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error] = useState<Error | null>(null);

  const { addNotification } = useNotification();

  const loadRoles = async () => {
    const response = await getRoles();
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to load Roles",
        text: response.message,
        type: "error",
      });
    } else {
      setRoles(response.roles);
    }
    setIsLoading(false);
  };

  useEffect(() => {
    void loadRoles();
  }, []);

  const deleteRole = async (id: string) => {
    const response = await removeRole(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to delete role",
        text: response.message,
        type: "error",
      });
    } else {
      const newRoles = roles.filter((role) => role.id !== id);
      setRoles(newRoles);
      addNotification({
        title: "Role deleted",
        text: "The role has been successfully deleted",
        type: "info",
      });
    }
  };

  return { roles, isLoading, error, deleteRole };
};

const NewUserRolesSection = ({
  config,
  roles,
}: {
  config: ConfigResponse;
  roles: RoleResponse[];
}) => {
  const { updateConfig } = useConfig();
  const { addNotification } = useNotification();
  const { control, handleSubmit } = useForm<{ newUserRoleIds: string[] }>({
    defaultValues: { newUserRoleIds: config.newUserRoleIds ?? [] },
  });

  const nonDefaultRoles = roles.filter((r) => !r.isDefault);

  const onSubmit = async ({ newUserRoleIds }: { newUserRoleIds: string[] }) => {
    await updateConfig({ newUserRoleIds });
    addNotification({
      title: "New user roles saved",
      text: "Default roles for new users have been updated",
      type: "info",
    });
  };

  return (
    <div className="mb-8 rounded-md border border-slate-200 p-4 dark:border-slate-700">
      <h2 className="mb-1 text-base font-medium">New User Roles</h2>
      <p className="mb-4 text-sm dark:text-slate-300">
        Roles assigned to every new user upon registration, in addition to the
        Default role.
      </p>
      <form onSubmit={(e) => void handleSubmit(onSubmit)(e)}>
        <Controller
          name="newUserRoleIds"
          control={control}
          render={({ field }) => {
            const selectedRoles = nonDefaultRoles.filter((r) =>
              (field.value ?? []).includes(r.id),
            );
            return (
              <div>
                <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                  Select roles
                </label>
                <Listbox
                  value={selectedRoles}
                  onChange={(roles) => field.onChange(roles.map((r) => r.id))}
                  multiple
                >
                  <div className="relative">
                    <ListboxButton className="relative w-64 cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-left text-slate-900 ring-1 ring-slate-300 focus:outline-none focus:ring-slate-400 dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 sm:text-sm sm:leading-6">
                      <span className="block truncate">
                        {selectedRoles.length === 0
                          ? "None"
                          : selectedRoles.map((r) => r.name).join(", ")}
                      </span>
                      <span className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2">
                        <ChevronUpDownIcon className="h-5 w-5 text-slate-400" />
                      </span>
                    </ListboxButton>
                    <ListboxOptions
                      anchor="bottom"
                      className="absolute z-10 mt-1 max-h-60 w-64 overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 sm:text-sm"
                    >
                      {nonDefaultRoles.map((role) => (
                        <ListboxOption
                          key={role.id}
                          value={role}
                          className="relative cursor-default select-none py-2 pl-3 pr-9 data-[focus]:bg-blue-100 data-[focus]:dark:bg-slate-700"
                        >
                          {({ selected }) => (
                            <>
                              <span
                                className={
                                  selected ? "font-semibold" : "font-normal"
                                }
                              >
                                {role.name}
                              </span>
                              {selected && (
                                <span className="absolute inset-y-0 right-0 flex items-center pr-4 text-indigo-600">
                                  <CheckIcon className="h-5 w-5" />
                                </span>
                              )}
                            </>
                          )}
                        </ListboxOption>
                      ))}
                    </ListboxOptions>
                  </div>
                </Listbox>
              </div>
            );
          }}
        />
        <div className="mt-4">
          <Button htmlType="submit" variant="primary">
            Save
          </Button>
        </div>
      </form>
    </div>
  );
};

const RoleSettings = () => {
  const { roles, isLoading, error, deleteRole } = useRoles();
  const { config } = useConfig();
  const navigate = useNavigate();

  const handleDeleteRole = async (role: RoleResponse) => {
    await deleteRole(role.id);
  };

  const handleRowClick = (role: RoleResponse) => {
    navigate(`/settings/roles/${role.id}`);
  };

  const handleCreateRole = () => {
    navigate("/settings/roles/new");
  };

  const columns: Column<RoleResponse>[] = [
    {
      header: "Role Name",
      accessor: "name",
    },
    {
      header: "Description",
      accessor: "description",
    },
    {
      header: "Type",
      render: (role) => (
        <span className="text-slate-600 dark:text-slate-400">
          {role.isDefault ? "System" : "Custom"}
        </span>
      ),
    },
  ];

  if (error) {
    return <div className="container mx-auto px-4 py-8">{error.message}</div>;
  }

  return (
    <div className="container mx-auto px-4 py-8">
      {config && <NewUserRolesSection config={config} roles={roles} />}
      <SettingsTable
        title="Roles"
        data={roles}
        columns={columns}
        keyExtractor={(role) => role.id}
        onRowClick={handleRowClick}
        onDelete={handleDeleteRole}
        canDelete={(role) => !role.isDefault}
        onCreate={handleCreateRole}
        createButtonLabel="Add Role"
        emptyMessage="No roles found. Create one to get started."
        loading={isLoading}
        testId="roles-table"
      />
    </div>
  );
};

export { useRoles, RoleSettings };
export default RoleSettings;
