import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import Button from "../../components/Button";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useEffect, useState } from "react";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import {
  PolicyPatch,
  PolicyResponse,
  RolePatch,
  RoleResponse,
  createRole,
  getRoles,
  patchRole,
  removeRole,
} from "../../api/RoleApi";
import ColorfulLabel from "../../components/ColorfulLabel";
import { useConnections } from "./connection/DatabaseSettings";
import { ConnectionResponse } from "../../api/DatasourceApi";
import DeleteConfirm from "../../components/DeleteConfirm";
import React from "react";
import ComboBox from "../../components/ComboBox";

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
  editRole: (id: string, role: RolePatch) => Promise<void>;
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

  const editRole = async (id: string, role: RolePatch) => {
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
  editRole: (role: RolePatch) => Promise<void>;
}) {
  const [policies, setPolicies] = useState<(PolicyPatch | PolicyResponse)[]>(
    props.role.policies,
  );
  const [name, setName] = useState(props.role.name);
  const [description, setDescription] = useState(props.role.description);
  const [selectedPermissions, setSelectedPermission] =
    useState("DATASOURCE_GET");

  const permissions = [
    "DATASOURCE_GET",
    "DATASOURCE_EDIT",
    "DATASOURCE_CREATE",
    "DATASOURCE_CONNECTION_GET",
    "DATASOURCE_CONNECTION_EDIT",
    "DATASOURCE_CONNECTION_CREATE",
    "EXECUTION_REQUEST_GET",
    "EXECUTION_REQUEST_EDIT",
    "EXECUTION_REQUEST_EXECUTE",
  ];

  const [selectedOption, setSelectedOption] = useState<{
    id: string;
    name: string;
  } | null>(null);

  const handleEditRole = (event: React.SyntheticEvent) => {
    event.preventDefault();
    void props.editRole({
      id: props.role.id,
      name,
      description,
      policies: policies,
    });
  };

  const addPolicy = (event: React.SyntheticEvent) => {
    event.preventDefault();
    if (!selectedOption) {
      return;
    }
    const newPolicy: PolicyPatch = {
      effect: "ALLOW",
      action: selectedPermissions,
      resource: selectedOption.id,
    };
    setPolicies([...policies, newPolicy]);
  };

  const removePermission = (
    connectionId: string,
    permission: string,
    effect: string,
    resource: string,
  ) => {
    const newPermissions = policies.filter(
      (p) =>
        p.resource !== resource ||
        p.action !== permission ||
        p.effect !== effect,
    );
    setPolicies(newPermissions);
  };

  const permissionText = (policy: PolicyResponse | PolicyPatch): string => {
    return `${policy.action} on ${policy.resource}`;
  };

  return (
    <form method="post" onSubmit={handleEditRole}>
      <div className="w-2xl shadow p-3 bg-white dark:bg-slate-950 rounded">
        <div className="flex flex-col mb-3">
          <InputField
            id="name"
            label="Name"
            value={name}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setName(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <InputField
            id="description"
            label="Description"
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
          <div
            key={permissionEntry.action + permissionEntry.resource}
            className="flex mb-3"
          >
            <div className="text-slate-400 text-sm">
              <ColorfulLabel
                text={permissionText(permissionEntry)}
                onDelete={() =>
                  removePermission(
                    permissionEntry.resource,
                    permissionEntry.action,
                    permissionEntry.effect,
                    permissionEntry.resource,
                  )
                }
              ></ColorfulLabel>
            </div>
          </div>
        ))}
        <div className="flex mb-3 border-t">
          <div>
            <label
              htmlFor="permissions"
              className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
            >
              Permission
            </label>
            <select
              id="permission"
              name="permission"
              className="mt-2 block w-full dark:bg-slate-900 rounded-md border-0 py-1.5 pl-3 pr-10 text-slate-900 ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6 dark:text-slate-50"
              defaultValue={permissions[0]}
              value={selectedPermissions}
              onChange={(e) => setSelectedPermission(e.target.value)}
            >
              {permissions.map((permission) => (
                <option>{permission}</option>
              ))}
            </select>
          </div>
          <ComboBox
            label="Resource"
            options={props.connections.map((connection) => {
              return { name: connection.displayName, id: connection.id };
            })}
            selectedOption={selectedOption}
            setSelectedOption={setSelectedOption}
          ></ComboBox>
          <Button className="ml-auto" onClick={addPolicy}>
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
      <div className="w-2xl shadow p-3 bg-white dark:bg-slate-950 rounded">
        <div className="flex flex-col mb-3">
          <InputField
            id="name"
            label="Name"
            value={name}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setName(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <InputField
            id="description"
            label="Description"
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
  const { connections } = useConnections();
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);

  const handleAddRole = () => {
    setShowAddModal(true);
  };

  const handleEditRole = (role: RoleResponse) => {
    setSelectedRole(role);
    setShowEditModal(true);
  };

  const handleEditRoleConfirm = async (role: RolePatch) => {
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
