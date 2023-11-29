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
import { useRoles } from "./RolesSettings";
import { RoleResponse } from "../../api/RoleApi";
import ColorfulLabel from "../../components/ColorfulLabel";

function UserForm(props: {
  disableModal: () => void;
  createNewUser: (
    email: string,
    password: string,
    fullName: string,
  ) => Promise<void>;
}) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");

  const saveUser = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    props
      .createNewUser(email, password, fullName)
      .then(() => {
        props.disableModal();
      })
      .catch((err) => console.log(err));
  };

  return (
    <form method="post" onSubmit={saveUser}>
      <div className="w-2xl shadow p-3 bg-white dark:bg-slate-950 rounded">
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
    void request();
  }, []);

  async function addRoleToUser(userId: string, roleId: string) {
    const currentUser = users.find((u) => u.id === userId);
    if (!currentUser) {
      return;
    }
    const newUser = await updateUser(userId, {
      roles: [...currentUser.roles.map((g) => g.id), roleId],
    });
    setUsers(users.map((u) => (u.id === userId ? newUser : u)));
  }

  async function createNewUser(
    email: string,
    password: string,
    fullName: string,
  ) {
    const newUser = await createUser({
      email: email,
      password: password,
      fullName: fullName,
    });
    setUsers([...users, newUser]);
  }

  async function removeRoleFromUser(userId: string, roleId: string) {
    const currentUser = users.find((u) => u.id === userId);
    if (!currentUser) {
      return;
    }
    const newUser = await updateUser(userId, {
      roles: currentUser.roles.filter((g) => g.id !== roleId).map((g) => g.id),
    });
    setUsers(users.map((u) => (u.id === userId ? newUser : u)));
  }

  return { addRoleToUser, users, createNewUser, removeRoleFromUser };
};

const UserRow = (props: {
  user: UserResponse;
  roles: RoleResponse[];
  addRoleToUser: (userId: string, roleId: string) => Promise<void>;
  removeRoleFromUser: (userId: string, roleId: string) => Promise<void>;
}) => {
  const [rolesDialogVisible, setRolesDialogVisible] = useState(false);

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
          {props.user.roles.map((role) => {
            return (
              <ColorfulLabel
                onDelete={() => {
                  void props.removeRoleFromUser(props.user.id, role.id);
                }}
                text={role.name}
              />
            );
          })}
          <ColorfulLabel
            text="Add Role"
            onClick={() => {
              setRolesDialogVisible(true);
            }}
            color="dark:bg-slate-700 border border-slate-400"
          />
          {rolesDialogVisible && (
            <Modal setVisible={setRolesDialogVisible}>
              <div className="w-2xl shadow p-3 bg-white dark:bg-slate-950 rounded">
                <div className="flex flex-col mb-3">
                  <div className="font-bold">Add Role</div>
                </div>
                <div className="flex flex-col mb-3">
                  <div className="flex flex-row flex-wrap">
                    {props.roles.map((role) => {
                      return (
                        <ColorfulLabel
                          onClick={() =>
                            void (async () => {
                              await props.addRoleToUser(props.user.id, role.id);
                              setRolesDialogVisible(false);
                            })()
                          }
                          text={role.name}
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
  const { addRoleToUser, users, createNewUser, removeRoleFromUser } =
    useUsers();

  const { roles } = useRoles();

  return (
    <div>
      <div className="flex flex-col justify-between w-2/3 mx-auto">
        <div className="flex flex-col min-h-60">
          {users.map((user) => (
            <UserRow
              user={user}
              roles={roles}
              addRoleToUser={addRoleToUser}
              removeRoleFromUser={removeRoleFromUser}
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
