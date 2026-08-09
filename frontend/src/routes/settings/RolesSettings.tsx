import { useEffect, useState } from "react";
import { RoleResponse, getRoles, removeRole } from "../../api/RoleApi";
import React from "react";
import { useNavigate } from "react-router-dom";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import SettingsTable, { Column } from "../../components/SettingsTable";
import { useHasPermission } from "../../hooks/permissions";
import NotAuthorized from "../../components/NotAuthorized";

// Pass enabled=false to skip the fetch entirely, e.g. when the user lacks role:get and the
// request could only 403.
const useRoles = (
  enabled = true,
): {
  roles: RoleResponse[];
  isLoading: boolean;
  error: Error | null;
  deleteRole: (id: string) => Promise<void>;
} => {
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const { addNotification } = useNotification();

  const loadRoles = async () => {
    setIsLoading(true);
    const response = await getRoles();
    if (isApiErrorResponse(response)) {
      setError(new Error(response.message));
      addNotification({
        title: "Failed to load Roles",
        text: response.message,
        type: "error",
      });
    } else {
      setRoles(response.roles);
      setError(null);
    }
    setIsLoading(false);
  };

  useEffect(() => {
    if (!enabled) {
      setIsLoading(false);
      return;
    }
    void loadRoles();
  }, [enabled]);

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

const RoleSettings = () => {
  // Without role:get the fetch could only 403, so skip it and show the forbidden state
  // directly. Any other load failure surfaces as the hook's error notification instead —
  // a 500 or network drop is not a permissions problem.
  const canListRoles = useHasPermission("role:get");
  const { roles, isLoading, deleteRole } = useRoles(canListRoles);
  const canEditRoles = useHasPermission("role:edit");
  const navigate = useNavigate();

  const handleDeleteRole = async (role: RoleResponse) => {
    await deleteRole(role.id);
  };

  const handleRowClick = (role: RoleResponse) => {
    void navigate(`/settings/roles/${role.id}`);
  };

  const handleCreateRole = () => {
    void navigate("/settings/roles/new");
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

  if (!canListRoles) {
    return (
      <div className="container mx-auto px-4 py-8">
        <NotAuthorized resource="the role settings" />
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <SettingsTable
        title="Roles"
        data={roles}
        columns={columns}
        keyExtractor={(role) => role.id}
        onRowClick={handleRowClick}
        onDelete={canEditRoles ? handleDeleteRole : undefined}
        canDelete={(role) => !role.isDefault}
        onCreate={canEditRoles ? handleCreateRole : undefined}
        createButtonLabel="Add Role"
        emptyMessage={
          canEditRoles
            ? "No roles found. Create one to get started."
            : "No roles found."
        }
        loading={isLoading}
        testId="roles-table"
      />
    </div>
  );
};

export { useRoles, RoleSettings };
export default RoleSettings;
