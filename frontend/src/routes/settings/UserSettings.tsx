import React, { useEffect, useState } from "react";
import Button from "../../components/Button";
import {
  UserResponse,
  createUser,
  fetchUsers,
  updateUser,
} from "../../api/UserApi";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import { useGroups } from "./GroupsSettings";
import { GroupResponse } from "../../api/GroupApi";
import ColorfulLabel from "../../components/ColorfulLabel";

function UserForm(props: {
  disableModal: () => void;
  createNewUser: (email: string, password: string, fullName: string) => void;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");

  const saveUser = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    await props.createNewUser(email, password, fullName);
    props.disableModal();
  };

  return (
    <form method="post" onSubmit={saveUser}>
      <div className="w-2xl shadow p-3 bg-white rounded">
        <div className="flex flex-col mb-3">
          <InputField
            id="email"
            name="Email"
            value={email}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setEmail(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <InputField
            id="password"
            name="Password"
            type="passwordlike"
            value={password}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setPassword(e.target.value)
            }
          />
        </div>
        <div className="flex flex-col mb-3">
          <InputField
            id="fullName"
            name="Full Name"
            value={fullName}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setFullName(e.target.value)
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

const useUsers = () => {
  const [users, setUsers] = useState<UserResponse[]>([]);
  useEffect(() => {
    async function request() {
      const apiUsers = await fetchUsers();
      setUsers(apiUsers);
    }
    request();
  }, []);

  async function addGroupToUser(userId: string, groupId: string) {
    const currentUser = users.find((u) => u.id === userId);
    if (!currentUser) {
      return;
    }
    const newUser = await updateUser(userId, {
      groups: [...currentUser.groups.map((g) => g.id), groupId],
    });
    setUsers(users.map((u) => (u.id === userId ? newUser : u)));
  }

  async function createNewUser(
    email: string,
    password: string,
    fullName: string
  ) {
    const newUser = await createUser({
      email: email,
      password: password,
      fullName: fullName,
    });
    setUsers([...users, newUser]);
  }

  async function removeGroupFromUser(userId: string, groupId: string) {
    const currentUser = users.find((u) => u.id === userId);
    if (!currentUser) {
      return;
    }
    const newUser = await updateUser(userId, {
      groups: currentUser.groups
        .filter((g) => g.id !== groupId)
        .map((g) => g.id),
    });
    setUsers(users.map((u) => (u.id === userId ? newUser : u)));
  }

  return { addGroupToUser, users, createNewUser, removeGroupFromUser };
};

const UserRow = (props: {
  user: UserResponse;
  groups: GroupResponse[];
  addGroupToUser: (userId: string, groupId: string) => void;
  removeGroupFromUser: (userId: string, groupId: string) => void;
}) => {
  const [groupsDialogVisible, setGroupsDialogVisible] = useState(false);

  return (
    <div className="flex flex-row">
      <div className="flex flex-row justify-between w-full shadow-sm p-2">
        <div className="flex flex-row w-1/3">
          <div className="font-bold">{props.user.fullName}</div>
        </div>
        <div className="flex flex-row text-slate-400 w-1/3">
          <div>{props.user.email}</div>
        </div>
        <div className="flex flex-row w-1/3 flex-wrap justify-end">
          {props.user.groups.map((group) => {
            return (
              <ColorfulLabel
                onDelete={() => {
                  props.removeGroupFromUser(props.user.id, group.id);
                }}
                text={group.name}
              />
            );
          })}
          <ColorfulLabel
            text="Add Group"
            onClick={() => {
              setGroupsDialogVisible(true);
            }}
            color="bg-white text-slate-700 border border-slate-400"
          />
          {groupsDialogVisible && (
            <Modal setVisible={setGroupsDialogVisible}>
              <div className="w-2xl shadow p-3 bg-white rounded">
                <div className="flex flex-col mb-3">
                  <div className="font-bold">Add Group</div>
                </div>
                <div className="flex flex-col mb-3">
                  <div className="flex flex-row flex-wrap">
                    {props.groups.map((group) => {
                      return (
                        <ColorfulLabel
                          onClick={() => {
                            props.addGroupToUser(props.user.id, group.id);
                            setGroupsDialogVisible(false);
                          }}
                          text={group.name}
                        />
                      );
                    })}
                  </div>
                </div>
              </div>
            </Modal>
          )}
        </div>
      </div>
    </div>
  );
};

const UserSettings = () => {
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const { addGroupToUser, users, createNewUser, removeGroupFromUser } =
    useUsers();

  const { groups, isLoading, error, deleteGroup, addGroup, editGroup } =
    useGroups();

  return (
    <div>
      <div className="flex flex-col justify-between w-2/3 mx-auto">
        <div className="flex flex-col min-h-60">
          {users.map((user) => (
            <UserRow
              user={user}
              groups={groups}
              addGroupToUser={addGroupToUser}
              removeGroupFromUser={removeGroupFromUser}
            />
          ))}
        </div>
        <div className="flex">
          <Button
            className="ml-auto my-2"
            onClick={() => setShowCreateUserModal(true)}
          >
            Add User
          </Button>
        </div>
        {showCreateUserModal && (
          <Modal setVisible={setShowCreateUserModal}>
            <UserForm
              disableModal={() => setShowCreateUserModal(false)}
              createNewUser={createNewUser}
            />
          </Modal>
        )}
      </div>
    </div>
  );
};

export default UserSettings;
