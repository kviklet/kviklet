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
import { isApiErrorResponse } from "../../api/Errors";
import { Error, Success } from "../../components/Alert";
import RoleComboBox from "./RoleComboBox";

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
      <div className="w-2xl rounded bg-white p-3 shadow dark:bg-slate-950">
        <div className="mb-3 flex flex-col">
          <InputField
            id="email"
            label="Email"
            value={email}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setEmail(e.target.value)
            }
            data-testid="email-input"
          />
        </div>
        <div className="mb-3 flex flex-col">
          <InputField
            id="password"
            label="Password"
            type="passwordlike"
            value={password}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setPassword(e.target.value)
            }
            data-testid="password-input"
          />
        </div>
        <div className="mb-3 flex flex-col">
          <InputField
            id="fullName"
            label="Full Name"
            value={fullName}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
              setFullName(e.target.value)
            }
            data-testid="name-input"
          />
        </div>
        <div className="mb-3 flex flex-col">
          <Button
            className="ml-auto"
            type="submit"
            dataTestId="create-user-button"
          >
            Create
          </Button>
        </div>
      </div>
    </form>
  );
}

export const useUsers = () => {
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  function clearNotifications() {
    setTimeout(() => {
      setError("");
      setSuccess("");
    }, 5000);
  }

  useEffect(() => {
    async function request() {
      const response = await fetchUsers();
      if (isApiErrorResponse(response)) {
        setError(response.message);
      } else {
        setUsers(response.users);
      }
    }
    void request();
  }, []);

  async function setRoles(userId: string, roles: RoleResponse[]) {
    const currentUser = users.find((u) => u.id === userId);
    if (!currentUser) {
      return false;
    }
    const response = await updateUser(userId, {
      roles: roles.map((g) => g.id),
    });
    if (isApiErrorResponse(response)) {
      setError(response.message);
      return false;
    }
    setUsers(users.map((u) => (u.id === userId ? response : u)));
    setSuccess("Roles updated");
    return true;
  }

  async function createNewUser(
    email: string,
    password: string,
    fullName: string,
  ) {
    try {
      const userResponse = await createUser({
        email: email,
        password: password,
        fullName: fullName,
      });
      if (isApiErrorResponse(userResponse)) {
        setError(userResponse.message);
      } else {
        setUsers([...users, userResponse]);
        setSuccess(`User created for email ${userResponse.email}`);
      }
    } catch (err) {
      setError("Something went wrong");
    }
    clearNotifications();
  }

  return {
    users,
    createNewUser,
    setRoles,
    error,
    success,
  };
};

const UserRow = (props: {
  user: UserResponse;
  roles: RoleResponse[];
  setRoles: (roles: RoleResponse[]) => Promise<boolean>;
}) => {
  return (
    <div className="flex flex-row" data-testid={`user-${props.user.email}`}>
      <div className="grid w-full grid-cols-2 p-2 shadow-sm md:grid-cols-3">
        <div className="flex flex-row">
          <div className="font-bold">{props.user.fullName}</div>
        </div>
        <div className="flex flex-row text-slate-400">
          <div>{props.user.email}</div>
        </div>
        <div className="flex flex-row flex-wrap justify-end">
          <RoleComboBox
            roles={props.user.roles}
            setRoles={props.setRoles}
            availableRoles={props.roles}
          ></RoleComboBox>
        </div>
      </div>
    </div>
  );
};

const UserSettings = () => {
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const { users, createNewUser, error, success, setRoles } = useUsers();

  const { roles } = useRoles();

  return (
    <div>
      {error && (
        <div className="mx-2 my-4 px-4 py-2">
          <Error>{error}</Error>
        </div>
      )}
      {success && (
        <div className="mx-2 my-4 px-4 py-2">
          <Success>{success}</Success>
        </div>
      )}
      <div className="mx-auto flex flex-col justify-between">
        <div className="flex flex-col">
          {users.map((user) => (
            <UserRow
              user={user}
              roles={roles}
              setRoles={(roles) => {
                return setRoles(user.id, roles);
              }}
            />
          ))}
        </div>
        <div className="flex">
          <Button
            className="my-2 ml-auto"
            onClick={() => setShowCreateUserModal(true)}
            dataTestId="add-user-button"
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
