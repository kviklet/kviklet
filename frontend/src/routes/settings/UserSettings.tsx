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
      <div className="w-2xl rounded bg-slate-50 p-3 shadow dark:bg-slate-900">
        <h2 className="mb-4 text-lg font-semibold">Create New User</h2>
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
        <div className="mb-3 flex flex-row justify-end space-x-2">
          <Button onClick={props.disableModal} htmlType="button">
            Cancel
          </Button>
          <Button
            htmlType="submit"
            variant="primary"
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
  const [loading, setLoading] = useState(true);

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
      setLoading(false);
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
    loading,
  };
};

const UserRow = (props: {
  user: UserResponse;
  roles: RoleResponse[];
  setRoles: (roles: RoleResponse[]) => Promise<boolean>;
}) => {
  return (
    <div
      className="flex flex-row border-b border-slate-200 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
      data-testid={`user-${props.user.email}`}
    >
      <div className="grid w-full grid-cols-2 px-6 py-4 md:grid-cols-3">
        <div className="flex items-center">
          <div className="font-medium text-slate-900 dark:text-slate-100">
            {props.user.fullName}
          </div>
        </div>
        <div className="flex items-center">
          <div className="text-slate-600 dark:text-slate-400">
            {props.user.email}
          </div>
        </div>
        <div className="flex items-center justify-end">
          <RoleComboBox
            roles={props.user.roles}
            setRoles={props.setRoles}
            availableRoles={props.roles}
          />
        </div>
      </div>
    </div>
  );
};

const UserSettings = () => {
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const { users, createNewUser, error, success, setRoles, loading } =
    useUsers();
  const { roles } = useRoles();

  if (loading) {
    return (
      <div className="container mx-auto px-4 py-8">
        <div className="flex h-64 items-center justify-center">
          <div className="text-slate-500 dark:text-slate-400">Loading...</div>
        </div>
      </div>
    );
  }

  return (
    <div className="container mx-auto px-4 py-8">
      {error && (
        <div className="mb-4">
          <Error>{error}</Error>
        </div>
      )}
      {success && (
        <div className="mb-4">
          <Success>{success}</Success>
        </div>
      )}

      {/* Header with Add User button */}
      <div className="mb-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
            Users
          </h2>
          <Button
            onClick={() => setShowCreateUserModal(true)}
            variant="primary"
            dataTestId="add-user-button"
          >
            Add User
          </Button>
        </div>
      </div>

      {/* User list */}
      {users.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <p className="text-slate-500 dark:text-slate-400">
            No users found. Create one to get started.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow dark:border-slate-700 dark:bg-slate-900">
          {/* Table header */}
          <div className="bg-slate-50 dark:bg-slate-800">
            <div className="grid grid-cols-2 px-6 py-3 md:grid-cols-3">
              <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                Name
              </div>
              <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                Email
              </div>
              <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300 md:text-right">
                Roles
              </div>
            </div>
          </div>

          {/* User rows */}
          <div>
            {users.map((user) => (
              <UserRow
                key={user.id}
                user={user}
                roles={roles}
                setRoles={(roles) => {
                  return setRoles(user.id, roles);
                }}
              />
            ))}
          </div>
        </div>
      )}

      {/* Create User Modal */}
      {showCreateUserModal && (
        <Modal setVisible={setShowCreateUserModal}>
          <UserForm
            disableModal={() => setShowCreateUserModal(false)}
            createNewUser={createNewUser}
          />
        </Modal>
      )}
    </div>
  );
};

export default UserSettings;
