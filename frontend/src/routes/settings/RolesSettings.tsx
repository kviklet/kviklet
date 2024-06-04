import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import Button from "../../components/Button";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useEffect, useState } from "react";
import Modal from "../../components/Modal";
import { RoleResponse, getRoles, removeRole } from "../../api/RoleApi";
import DeleteConfirm from "../../components/DeleteConfirm";
import React from "react";
import { Link } from "react-router-dom";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";

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
    await removeRole(id);

    const newRoles = roles.filter((role) => role.id !== id);
    setRoles(newRoles);
  };

  return { roles, isLoading, error, deleteRole };
};

const Table = ({
  roles,
  handleDeleteRole,
}: {
  roles: RoleResponse[];
  handleDeleteRole: (role: RoleResponse) => void;
}) => {
  return (
    <div className="flex w-full flex-col">
      <div className="overflow-x-auto">
        <div className="inline-block min-w-full align-middle">
          <div className="overflow-hidden border-b border-slate-200 shadow dark:border-slate-800 sm:rounded-lg">
            <div className="min-w-full divide-y divide-slate-200 dark:divide-slate-800">
              <div className="grid grid-cols-3 bg-slate-50 text-left text-slate-700 dark:bg-slate-900 dark:text-slate-200">
                <div className="px-6 py-3 text-xs font-medium uppercase tracking-wider">
                  Role Name
                </div>
                <div className="px-6 py-3 text-xs font-medium uppercase tracking-wider">
                  Description
                </div>
                <div className="relative px-6 py-3">
                  <span className="sr-only">Edit</span>
                </div>
              </div>
              <div>
                {roles.map((role) => (
                  <Link
                    to={`/settings/roles/${role.id}`}
                    key={role.id}
                    className="group grid grid-cols-3 hover:bg-slate-50 dark:hover:bg-slate-800"
                  >
                    <div className="whitespace-nowrap px-6 py-4 text-sm font-medium text-slate-900 dark:text-slate-50">
                      {role.name}
                    </div>
                    <div className="whitespace-nowrap px-6 py-4 text-sm text-slate-500 dark:text-slate-200">
                      {role.description}
                    </div>
                    {!role.isDefault && (
                      <div className="flex items-center justify-end whitespace-nowrap px-6 py-4 text-right text-sm font-medium">
                        <button
                          onClick={(e) => {
                            e.preventDefault();
                            handleDeleteRole(role);
                          }}
                          className="text-slate-400 hover:text-slate-900"
                        >
                          <FontAwesomeIcon icon={solid("trash")} />
                        </button>
                      </div>
                    )}
                  </Link>
                ))}
              </div>
            </div>
            {roles.length === 0 && (
              <div className="flex flex-row items-center justify-center p-5">
                <div className="text-slate-400">No roles found</div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

const RoleSettings = () => {
  const { roles, isLoading, error, deleteRole } = useRoles();
  const [selectedRole, setSelectedRole] = useState<RoleResponse | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  const handleDeleteRole = (role: RoleResponse) => {
    setSelectedRole(role);
    setShowDeleteModal(true);
  };

  if (isLoading) {
    return <div>Loading...</div>;
  }

  if (error) {
    return <div>{error.message}</div>;
  }

  return (
    <div>
      <div className="flex flex-col items-center justify-between">
        <Table roles={roles} handleDeleteRole={handleDeleteRole} />
        <Link to="/settings/roles/new" className="ml-auto">
          <Button type="primary" className="  mt-2">
            {"Add Role"}
          </Button>
        </Link>
      </div>
      {showDeleteModal && selectedRole && (
        <Modal setVisible={setShowDeleteModal}>
          <DeleteConfirm
            title="Delete Role"
            message={`Are you sure you want to delete role ${selectedRole.name}?`}
            onConfirm={async () => {
              await deleteRole(selectedRole.id);
              setShowDeleteModal(false);
            }}
            onCancel={() => setShowDeleteModal(false)}
          />
        </Modal>
      )}
    </div>
  );
};

export { useRoles, RoleSettings };
export default RoleSettings;
