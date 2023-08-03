import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import Button from "../../components/Button";
import { IconDefinition } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useEffect, useState } from "react";
import { z } from "zod";
import { Link } from "react-router-dom";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import { type } from "os";
import {
  GroupResponse,
  PermissionResponse,
  createGroup,
  getGroups,
  patchGroup,
  removeGroup,
} from "../../api/GroupApi";
import ColorfulLabel from "../../components/ColorfulLabel";
import { useDatasources } from "./DatabaseSettings";
import { ConnectionResponse } from "../../api/DatasourceApi";
import DeleteConfirm from "../../components/DeleteConfirm";

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
        <div className="absolute bottom-0 left-0 bg-gray-800 text-white text-xs rounded p-1">
          {text}
        </div>
      )}
    </div>
  );
};

//icons
const createIcon = (icon: IconDefinition) => {
  return <FontAwesomeIcon icon={icon} />;
};
const EditOutlined = createIcon(solid("edit"));

const permissionsToText = (permissions: PermissionResponse[]) => {
  return permissions
    .map((permission) => connectionPermissionsToText(permission))
    .join("; ");
};

const connectionPermissionsToText = (permissions: PermissionResponse) => {
  return `${permissions.scope}: ${permissions.permissions.join(", ")}`;
};

const useGroups = (): {
  groups: GroupResponse[];
  isLoading: boolean;
  error: Error | null;
  deleteGroup: (id: string) => void;
  addGroup: (name: string, description: string) => void;
  editGroup: (id: string, group: GroupResponse) => void;
} => {
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);

  const loadGroups = async () => {
    const loadedGroups = await getGroups();
    setGroups(loadedGroups);
    setIsLoading(false);
  };

  useEffect(() => {
    loadGroups();
  }, []);

  const deleteGroup = async (id: string) => {
    await removeGroup(id);

    const newGroups = groups.filter((group) => group.id !== id);
    setGroups(newGroups);
  };

  const addGroup = async (name: string, description: string) => {
    const group = await createGroup({
      name,
      description,
    });
    const newGroups = [...groups, group];
    setGroups(newGroups);
  };

  const editGroup = async (id: string, group: GroupResponse) => {
    const newGroup = await patchGroup(id, group);
    const newGroups = groups.map((group) => {
      if (group.id === id) {
        return newGroup;
      }
      return group;
    });
    setGroups(newGroups);
  };

  return { groups, isLoading, error, deleteGroup, addGroup, editGroup };
};

const Table = ({
  groups,
  handleEditGroup,
  handleDeleteGroup,
}: {
  groups: GroupResponse[];
  handleEditGroup: (group: GroupResponse) => void;
  handleDeleteGroup: (group: GroupResponse) => void;
}) => {
  return (
    <div className="flex flex-col w-full">
      <div className="overflow-x-auto">
        <div className="align-middle inline-block min-w-full">
          <div className="shadow overflow-hidden border-b border-gray-200 sm:rounded-lg">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50 text-left">
                <tr>
                  <th
                    scope="col"
                    className="px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"
                  >
                    Group Name
                  </th>
                  <th
                    scope="col"
                    className="px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"
                  >
                    Description
                  </th>
                  <th
                    scope="col"
                    className="px-6 py-3 text-xs font-medium text-gray-500 uppercase tracking-wider"
                  >
                    Permissions
                  </th>
                  <th scope="col" className="relative px-6 py-3">
                    <span className="sr-only">Edit</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {groups.map((group) => (
                  <tr key={group.id}>
                    <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">
                      {group.name}
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                      {group.description}
                    </td>
                    <td>
                      <Tooltip text={permissionsToText(group.permissions)}>
                        <div className="text-gray-400 hover:text-gray-900">
                          <FontAwesomeIcon icon={solid("eye")} />
                        </div>
                      </Tooltip>
                    </td>
                    <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                      <div className="flex flex-row">
                        <button
                          onClick={() => handleEditGroup(group)}
                          className="text-gray-400 hover:text-gray-900 mr-2"
                        >
                          Edit
                        </button>
                        <button
                          onClick={() => handleDeleteGroup(group)}
                          className="text-gray-400 hover:text-gray-900"
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
          {groups.length === 0 && (
            <div className="flex flex-row justify-center items-center p-5">
              <div className="text-gray-400">No groups found</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
function EditGroupForm(props: {
  connections: ConnectionResponse[];
  group: GroupResponse;
  editGroup: (group: GroupResponse) => Promise<void>;
}) {
  const [permissions, setPermissions] = useState(props.group.permissions);
  const [name, setName] = useState(props.group.name);
  const [description, setDescription] = useState(props.group.description);
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
        return "bg-gray-200 text-gray-800";
    }
  };

  const permissionOptions = [
    { value: "READ", label: "Read" },
    { value: "WRITE", label: "Write" },
    { value: "EXECUTE", label: "Execute" },
  ];

  const handleEditGroup = (event: React.SyntheticEvent) => {
    event.preventDefault();
    props.editGroup({
      id: props.group.id,
      name,
      description,
      permissions,
    });
  };

  const removePermission = (connectionId: string, permission: string) => {
    const newPermissions = permissions.map((permissionEntry) => {
      if (permissionEntry.scope === connectionId) {
        return {
          scope: permissionEntry.scope,
          permissions: permissionEntry.permissions.filter(
            (p) => p !== permission
          ),
        };
      }
      return permissionEntry;
    });
    setPermissions(newPermissions);
  };

  const addPermission = (connectionId: string, permission: string) => {
    const newPermissions = permissions.map((permissionEntry) => {
      if (permissionEntry.scope === connectionId) {
        return {
          scope: permissionEntry.scope,
          permissions: [...permissionEntry.permissions, permission],
        };
      }
      return permissionEntry;
    });
    setPermissions(newPermissions);
  };

  return (
    <form method="post" onSubmit={handleEditGroup}>
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
            value={description || ""}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setDescription(e.target.value)
            }
          />
        </div>
        <div>
          <div className="text-gray-400 text-sm mb-2">Permissions</div>
        </div>
        {permissions.map((permissionEntry, index) => (
          <div key={permissionEntry.scope} className="flex flex-col mb-3">
            <div>{permissionEntry.scope}</div>
            <div className="text-gray-400 text-sm">
              {permissionEntry.permissions.map((permission) => (
                <ColorfulLabel
                  text={permission}
                  color={mapActionToColor(permission)}
                  onDelete={() =>
                    removePermission(permissionEntry.scope, permission)
                  }
                ></ColorfulLabel>
              ))}
            </div>
          </div>
        ))}
        <div className="flex mb-3 border-t">
          <select
            name="connection"
            className="py-1 px-4 m-2  border-gray-200 border rounded-md text-sm focus:border-blue-500 focus:ring-blue-50"
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
            className="py-1 px-4 m-2 border border-gray-200 rounded-md text-sm focus:border-blue-500 focus:ring-blue-50"
            value={permissionToAdd}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              setPermissionToAdd(e.target.value)
            }
          >
            {permissionOptions.map((option) => (
              <option value={option.value}>{option.label}</option>
            ))}
          </select>
          <Button
            className="ml-auto"
            onClick={() => addPermission(connectionToAdd, permissionToAdd)}
          >
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

function GroupForm(props: {
  handleSaveGroup: (name: string, description: string) => void;
  handleCancel: () => void;
}) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");

  const saveGroup = (event: React.SyntheticEvent) => {
    event.preventDefault();
    props.handleSaveGroup(name, description);
  };

  return (
    <form method="post" onSubmit={saveGroup}>
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

const GroupSettings = () => {
  const { groups, isLoading, error, deleteGroup, addGroup, editGroup } =
    useGroups();
  const [selectedGroup, setSelectedGroup] = useState<GroupResponse | null>(
    null
  );
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const { datasources } = useDatasources();
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);

  const connections = datasources.flatMap((datasource) =>
    datasource.datasourceConnections.map((connection) => connection)
  );

  const handleAddGroup = () => {
    setShowAddModal(true);
  };

  const handleEditGroup = (group: GroupResponse) => {
    setSelectedGroup(group);
    setShowEditModal(true);
  };

  const handleEditGroupConfirm = async (group: GroupResponse) => {
    await editGroup(group.id, group);
    setShowEditModal(false);
  };

  const handleDeleteGroup = (group: GroupResponse) => {
    setSelectedGroup(group);
    setShowDeleteModal(true);
  };

  const handleAddGroupCancel = () => {
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
          groups={groups}
          handleEditGroup={handleEditGroup}
          handleDeleteGroup={handleDeleteGroup}
        />
        <Button
          type="primary"
          onClick={handleAddGroup}
          className="ml-auto mt-2"
        >
          {"Add Group"}
        </Button>
      </div>
      {showEditModal && selectedGroup && (
        <Modal setVisible={setShowEditModal}>
          <EditGroupForm
            connections={connections}
            group={selectedGroup}
            editGroup={handleEditGroupConfirm}
          ></EditGroupForm>
        </Modal>
      )}
      {showAddModal && (
        <Modal setVisible={setShowAddModal}>
          <GroupForm
            handleSaveGroup={addGroup}
            handleCancel={handleAddGroupCancel}
          ></GroupForm>
        </Modal>
      )}
      {showDeleteModal && selectedGroup && (
        <Modal setVisible={setShowDeleteModal}>
          <DeleteConfirm
            title="Delete Group"
            message={`Are you sure you want to delete group ${selectedGroup.name}?`}
            onConfirm={() => deleteGroup(selectedGroup.id)}
            onCancel={() => setShowDeleteModal(false)}
          />
        </Modal>
      )}
    </div>
  );
};

export { useGroups, GroupSettings };
export default GroupSettings;
