import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import Button from "../../components/Button";
import { IconDefinition } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { useEffect, useState } from "react";
import { z } from "zod";
import { Link } from "react-router-dom";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";

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

const PermissionsObject = z.object({
  connection: z.string(),
  permissions: z.array(z.string()),
});
type Permissions = z.infer<typeof PermissionsObject>;

const permissionsToText = (permissions: Permissions[]) => {
  return permissions
    .map((permission) => connectionPermissionsToText(permission))
    .join("; ");
};

const connectionPermissionsToText = (permissions: Permissions) => {
  return `${permissions.connection}: ${permissions.permissions.join(", ")}`;
};

const GroupObject = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string(),
  permissions: z.array(PermissionsObject),
});

const useGroups = (): {
  groups: Group[];
  isLoading: boolean;
  error: Error | null;
  deleteGroup: (id: string) => void;
  addGroup: (group: Group) => void;
  editGroup: (id: string, group: Group) => void;
} => {
  const [groups, setGroups] = useState<Group[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    setIsLoading(true);
    setError(null);

    //fetch groups
    const groups = [
      {
        id: "1",
        name: "group1",
        description: "this is group1",
        permissions: [
          {
            connection: "connection1",
            permissions: ["permission1", "permission2"],
          },
          {
            connection: "connection2",
            permissions: ["permission1", "permission2"],
          },
        ],
      },
      {
        id: "2",
        name: "group2",
        description: "this is group2",
        permissions: [
          {
            connection: "connection1",
            permissions: ["permission1", "permission2"],
          },
          {
            connection: "connection2",
            permissions: ["permission1", "permission2"],
          },
        ],
      },
    ];

    setGroups(groups);
    setIsLoading(false);
  }, []);

  const deleteGroup = (id: string) => {
    //delete group

    //update groups
    const newGroups = groups.filter((group) => group.id !== id);
    setGroups(newGroups);
  };

  const addGroup = (group: Group) => {
    //add group

    //update groups
    const newGroups = [...groups, group];
    setGroups(newGroups);
  };

  const editGroup = (id: string, group: Group) => {
    //edit group

    //update groups
    const newGroups = groups.map((group) => {
      if (group.id === id) {
        return group;
      }
      return group;
    });
    setGroups(newGroups);
  };

  return { groups, isLoading, error, deleteGroup, addGroup, editGroup };
};

type Group = z.infer<typeof GroupObject>;

const Table = ({
  groups,
  handleEditGroup,
  handleDeleteGroup,
}: {
  groups: Group[];
  handleEditGroup: (group: Group) => void;
  handleDeleteGroup: (group: Group) => void;
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
function PermissionForm(props: {
  group: Group;
  handleSavePermissions: (group: Group) => Promise<void>;
}) {
  const [permissions, setPermissions] = useState(props.group.permissions);

  const handlePermissionChange = (index: number, value: string) => {
    const newPermissions = [...permissions];
    newPermissions[index].permissions = value.split(", ");
    setPermissions(newPermissions);
  };

  const savePermissions = async (e: React.SyntheticEvent) => {
    e.preventDefault();
    const group = {
      id: props.group.id,
      name: props.group.name,
      description: props.group.description,
      permissions: permissions,
    };

    await props.handleSavePermissions(group);
  };

  return (
    <form method="post" onSubmit={savePermissions}>
      <div className="w-2xl shadow p-3 bg-white rounded">
        {permissions.map((permission, index) => (
          <div key={permission.connection} className="flex flex-col mb-3">
            <label
              htmlFor={permission.connection}
              className="text-sm font-medium text-gray-700"
            >
              {permission.connection}
            </label>
            <InputField
              id={permission.connection}
              name="Name"
              value={connectionPermissionsToText(permission)}
              onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                handlePermissionChange(index, e.target.value)
              }
            />
          </div>
        ))}
        <Button type="submit">Save Permissions</Button>
      </div>
    </form>
  );
}

const GroupSettings = () => {
  const { groups, isLoading, error, deleteGroup, addGroup, editGroup } =
    useGroups();
  const [selectedGroup, setSelectedGroup] = useState<Group | null>(null);
  const [showModal, setShowModal] = useState(false);
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);

  const handleAddGroup = () => {
    setShowAddModal(true);
  };

  const handleEditGroup = (group: Group) => {
    setSelectedGroup(group);
    setShowEditModal(true);
  };

  const handleDeleteGroup = (group: Group) => {
    setSelectedGroup(group);
    setShowDeleteModal(true);
  };

  const handleDeleteGroupConfirm = () => {
    if (selectedGroup) {
      deleteGroup(selectedGroup.id);
    }
    setShowDeleteModal(false);
  };

  const handleAddGroupConfirm = (group: Group) => {
    addGroup(group);
    setShowAddModal(false);
  };

  const handleEditGroupConfirm = async (group: Group) => {
    if (selectedGroup) {
      editGroup(selectedGroup.id, group);
    }
    setShowEditModal(false);
  };

  const handleDeleteGroupCancel = () => {
    setShowDeleteModal(false);
  };

  const handleAddGroupCancel = () => {
    setShowAddModal(false);
  };

  const handleEditGroupCancel = () => {
    setShowEditModal(false);
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
          <PermissionForm
            group={selectedGroup}
            handleSavePermissions={handleEditGroupConfirm}
          ></PermissionForm>
        </Modal>
      )}
    </div>
  );
};

export default GroupSettings;
