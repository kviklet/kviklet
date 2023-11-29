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
  patchRole,
  removeRole,
} from "../../api/RoleApi";
import ColorfulLabel from "../../components/ColorfulLabel";
import { useDatasources } from "./DatabaseSettings";
import { ConnectionResponse } from "../../api/DatasourceApi";
import DeleteConfirm from "../../components/DeleteConfirm";
import React from "react";

const Tooltip = ({
  children,
  text,
}: {
  children: React.ReactNode;
  text: string;
}) => {
  const [hovered, setHovered] = useState(false);

  return (
    <div
      className="relative"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      {children}
      {hovered && (
        <div className="absolute bottom-0 left-0 bg-slate-800 text-white text-xs rounded p-1">
          {text}
        </div>
      )}
    </div>
  );
};

const useRoles = (): {
  roles: RoleResponse[];
  isLoading: boolean;
  error: Error | null;
  deleteRole: (id: string) => Promise<void>;
  addRole: (name: string, description: string) => Promise<void>;
  editRole: (id: string, role: RoleResponse) => Promise<void>;
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

  const editRole = async (id: string, role: RoleResponse) => {
    const newRole = await patchRole(id, role);
    const newRoles = roles.map((role) => {
      if (role.id === id) {
        return newRole;
      }
      return role;
    });
    setRoles(newRoles);
  };

  return { roles, isLoading, error, deleteRole, addRole, editRole };
};

const Table = ({
  roles,
  handleEditRole,
  handleDeleteRole,
}: {
  roles: RoleResponse[];
  handleEditRole: (role: RoleResponse) => void;
  handleDeleteRole: (role: RoleResponse) => void;
}) => {
  return (
    <div className="flex flex-col w-full">
      <div className="overflow-x-auto">
        <div className="align-middle inline-block min-w-full">
          <div className="shadow overflow-hidden border-b border-slate-200 sm:rounded-lg">
            <table className="min-w-full divide-y divide-slate-200">
              <thead className="bg-slate-50 dark:bg-slate-900 dark:text-slate-200 text-slate-700 text-left">
                <tr>
                  <th
                    scope="col"
                    className="px-6 py-3 text-xs font-medium uppercase tracking-wider"
                  >
                    Role Name
                  </th>
                  <th
                    scope="col"
                    className="px-6 py-3 text-xs font-medium uppercase tracking-wider"
                  >
                    Description
                  </th>
                  <th
                    scope="col"
                    className="px-6 py-3 text-xs font-medium uppercase tracking-wider"
                  >
                    Permissions
                  </th>
                  <th scope="col" className="relative px-6 py-3">
                    <span className="sr-only">Edit</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {roles.map((role) => (
                  <tr key={role.id}>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900 dark:text-slate-50">
                      {role.name}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500 dark:text-slate-200">
                      {role.description}
                    </td>
                    <td>
                      <Tooltip
                        text={role.policies
                          .map((policy) => policy.action)
                          .join(",")}
                      >
                        <div className="text-slate-400 hover:text-slate-900">
                          <FontAwesomeIcon icon={solid("eye")} />
                        </div>
                      </Tooltip>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <div className="flex flex-row">
                        <button
                          onClick={() => handleEditRole(role)}
                          className="text-slate-400 hover:text-slate-900 mr-2"
                        >
                          Edit
                        </button>
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
            <div className="flex flex-row justify-center items-center p-5">
              <div className="text-slate-400">No roles found</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
function EditRoleForm(props: {
  connections: ConnectionResponse[];
  role: RoleResponse;
  editRole: (role: RoleResponse) => Promise<void>;
}) {
  const [policies, setPolicies] = useState(props.role.policies);
  const [name, setName] = useState(props.role.name);
  const [description, setDescription] = useState(props.role.description);
  const [connectionToAdd, setConnectionToAdd] = useState("");
  const [permissionToAdd, setPermissionToAdd] = useState("");
  const mapActionToColor = (action: string) => {
    switch (action) {
      case "READ":
        return "bg-green-200 text-green-800";
      case "WRITE":
        return "bg-blue-200 text-blue-800";
      case "EXECUTE":
        return "bg-yellow-200 text-yellow-800";
      default:
        return "bg-slate-200 text-slate-800";
    }
  };

  const permissionOptions = [
    { value: "READ", label: "Read" },
    { value: "WRITE", label: "Write" },
    { value: "EXECUTE", label: "Execute" },
  ];

  const handleEditRole = (event: React.SyntheticEvent) => {
    event.preventDefault();
    void props.editRole({
      id: props.role.id,
      name,
      description,
      policies: policies,
    });
  };

  const removePermission = (connectionId: string, permission: string) => {
    const newPermissions = policies.filter((p) => p.id !== permission);
    setPolicies(newPermissions);
  };

  return (
    <form method="post" onSubmit={handleEditRole}>
      <div className="w-2xl shadow p-3 bg-white dark:bg-slate-950 rounded">
        <div className="flex flex-col mb-3">
          <InputField
            id="name"
            name="Name"
            value={name}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setName(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <InputField
            id="description"
            name="Description"
            value={description || ""}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setDescription(e.target.value)
            }
          />
        </div>
        <div>
          <div className="text-slate-400 text-sm mb-2">Permissions</div>
        </div>
        {policies.map((permissionEntry) => (
          <div key={permissionEntry.resource} className="flex flex-col mb-3">
            <div>{permissionEntry.resource}</div>
            <div className="text-slate-400 text-sm">
              <ColorfulLabel
                text={permissionEntry.action}
                color={mapActionToColor(permissionEntry.action)}
                onDelete={() =>
                  removePermission(permissionEntry.resource, permissionEntry.id)
                }
              ></ColorfulLabel>
            </div>
          </div>
        ))}
        <div className="flex mb-3 border-t">
          <select
            name="connection"
            className="py-1 px-4 m-2  border-slate-200 border rounded-md text-sm focus:border-blue-500 focus:ring-blue-50"
            value={connectionToAdd}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              setConnectionToAdd(e.target.value)
            }
          >
            {props.connections.map((connection) => (
              <option value={connection.id}>{connection.displayName}</option>
            ))}
          </select>
          <select
            name="permission"
            className="py-1 px-4 m-2 border border-slate-200 rounded-md text-sm focus:border-blue-500 focus:ring-blue-50"
            value={permissionToAdd}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              setPermissionToAdd(e.target.value)
            }
          >
            {permissionOptions.map((option) => (
              <option value={option.value}>{option.label}</option>
            ))}
          </select>
          <Button className="ml-auto" onClick={() => {}}>
            Add Permission
          </Button>
        </div>
        <div className="flex flex-col mb-3">
          <Button type="submit" className="ml-auto">
            Save Permissions
          </Button>
        </div>
      </div>
    </form>
  );
}

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
      <div className="w-2xl shadow p-3 bg-white rounded">
        <div className="flex flex-col mb-3">
          <InputField
            id="name"
            name="Name"
            value={name}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setName(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <InputField
            id="description"
            name="Description"
            value={description}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setDescription(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <Button className="ml-auto" type="submit">
            Create
          </Button>
        </div>
      </div>
    </form>
  );
}

const RoleSettings = () => {
  const { roles, isLoading, error, deleteRole, addRole, editRole } = useRoles();
  const [selectedRole, setSelectedRole] = useState<RoleResponse | null>(null);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const { datasources } = useDatasources();
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);

  const connections = datasources.flatMap((datasource) =>
    datasource.datasourceConnections.map((connection) => connection),
  );

  const handleAddRole = () => {
    setShowAddModal(true);
  };

  const handleEditRole = (role: RoleResponse) => {
    setSelectedRole(role);
    setShowEditModal(true);
  };

  const handleEditRoleConfirm = async (role: RoleResponse) => {
    await editRole(role.id, role);
    setShowEditModal(false);
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
      <div className="flex justify-between items-center flex-col">
        <Table
          roles={roles}
          handleEditRole={handleEditRole}
          handleDeleteRole={handleDeleteRole}
        />
        <Button type="primary" onClick={handleAddRole} className="ml-auto mt-2">
          {"Add Role"}
        </Button>
      </div>
      {showEditModal && selectedRole && (
        <Modal setVisible={setShowEditModal}>
          <EditRoleForm
            connections={connections}
            role={selectedRole}
            editRole={handleEditRoleConfirm}
          ></EditRoleForm>
        </Modal>
      )}
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
