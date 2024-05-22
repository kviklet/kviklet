import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import Button from "../../components/Button";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useEffect, useState } from "react";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import {
  RoleResponse,
  createRole,
  getRoles,
  removeRole,
} from "../../api/RoleApi";
import DeleteConfirm from "../../components/DeleteConfirm";
import React from "react";
import { Link } from "react-router-dom";

const useRoles = (): {
  roles: RoleResponse[];
  isLoading: boolean;
  error: Error | null;
  deleteRole: (id: string) => Promise<void>;
  addRole: (name: string, description: string) => Promise<void>;
} => {
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error] = useState<Error | null>(null);

  const loadRoles = async () => {
    const loadedRoles = await getRoles();
    setRoles(loadedRoles);
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

  const addRole = async (name: string, description: string) => {
    const role = await createRole({
      name,
      description,
    });
    const newRoles = [...roles, role];
    setRoles(newRoles);
  };


  return { roles, isLoading, error, deleteRole, addRole };
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
          <div className="overflow-hidden border-b border-slate-200 shadow sm:rounded-lg">
            <table className="min-w-full table-auto divide-y divide-slate-200">
              <thead className="bg-slate-50 text-left text-slate-700 dark:bg-slate-900 dark:text-slate-200">
                <tr>
                  <th className="px-6 py-3 text-xs font-medium uppercase tracking-wider">
                    Role Name
                  </th>
                  <th className="px-6 py-3 text-xs font-medium uppercase tracking-wider">
                    Description
                  </th>
                  <th className="relative px-6 py-3">
                    <span className="sr-only">Edit</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {roles.map((role) => (
                  <tr key={role.id}>
                    <td className="whitespace-nowrap px-6 py-4 text-sm font-medium text-slate-900 dark:text-slate-50 ">
                      <Link
                        to={`/settings/roles/${role.id}`}
                        className="block hover:bg-slate-50 dark:hover:bg-slate-800"
                      >
                        {role.name}
                      </Link>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-500 dark:text-slate-200">
                      <Link
                        to={`/settings/roles/${role.id}`}
                        className="block hover:bg-slate-50 dark:hover:bg-slate-800"
                      >
                        {role.description}
                      </Link>
                    </td>
                    <td className="whitespace-nowrap px-6 py-4 text-right text-sm font-medium">
                      <div className="flex flex-row">
                        <button
                          onClick={() => handleDeleteRole(role)}
                          className="text-slate-400 hover:text-slate-900"
                        >
                          <FontAwesomeIcon icon={solid("trash")} />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {roles.length === 0 && (
            <div className="flex flex-row items-center justify-center p-5">
              <div className="text-slate-400">No roles found</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

function RoleForm(props: {
  handleSaveRole: (name: string, description: string) => Promise<void>;
  handleCancel: () => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const saveRole = (event: React.SyntheticEvent) => {
    event.preventDefault();
    void props.handleSaveRole(name, description);
  };

  return (
    <form method="post" onSubmit={saveRole}>
      <div className="w-2xl rounded bg-white p-3 shadow dark:bg-slate-950">
        <div className="mb-3 flex flex-col">
          <InputField
            id="name"
            label="Name"
            value={name}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setName(e.target.value)
            }
          />
        </div>
        <div className="mb-3 flex flex-col">
          <InputField
            id="description"
            label="Description"
            value={description}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setDescription(e.target.value)
            }
          />
        </div>
        <div className="mb-3 flex flex-col">
          <Button className="ml-auto" type="submit">
            Create
          </Button>
        </div>
      </div>
    </form>
  );
}

const RoleSettings = () => {
  const { roles, isLoading, error, deleteRole, addRole } = useRoles();
  const [selectedRole, setSelectedRole] = useState<RoleResponse | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);

  const handleAddRole = () => {
    setShowAddModal(true);
  };

  const handleDeleteRole = (role: RoleResponse) => {
    setSelectedRole(role);
    setShowDeleteModal(true);
  };

  const handleAddRoleCancel = () => {
    setShowAddModal(false);
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
        <Button type="primary" onClick={handleAddRole} className="ml-auto mt-2">
          {"Add Role"}
        </Button>
      </div>
      {showAddModal && (
        <Modal setVisible={setShowAddModal}>
          <RoleForm
            handleSaveRole={addRole}
            handleCancel={handleAddRoleCancel}
          ></RoleForm>
        </Modal>
      )}
      {showDeleteModal && selectedRole && (
        <Modal setVisible={setShowDeleteModal}>
          <DeleteConfirm
            title="Delete Role"
            message={`Are you sure you want to delete role ${selectedRole.name}?`}
            onConfirm={() => deleteRole(selectedRole.id)}
            onCancel={() => setShowDeleteModal(false)}
          />
        </Modal>
      )}
    </div>
  );
};

export { useRoles, RoleSettings };
export default RoleSettings;
