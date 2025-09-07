import { useEffect, useState } from "react";
import { RoleResponse, getRoles, removeRole } from "../../api/RoleApi";
import React from "react";
import { useNavigate } from "react-router-dom";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import SettingsTable, { Column } from "../../components/SettingsTable";

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

const RoleSettings = () => {
  const { roles, isLoading, error, deleteRole } = useRoles();
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
