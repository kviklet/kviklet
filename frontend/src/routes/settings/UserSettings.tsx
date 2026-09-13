import React, { useContext, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import Button from "../../components/Button";
import {
  CreateUserRequest,
  UserResponse,
  createUser,
  createUserRequestSchema,
  fetchUsers,
  setUserActive,
  updateUser,
} from "../../api/UserApi";
import InputField from "../../components/InputField";
import Modal from "../../components/Modal";
import { useRoles } from "./RolesSettings";
import { RoleResponse } from "../../api/RoleApi";
import { isApiErrorResponse } from "../../api/Errors";
import { Error, Success } from "../../components/Alert";
import RoleComboBox from "./RoleComboBox";
import RequirePermission from "../../components/RequirePermission";
import { useHasPermission } from "../../hooks/permissions";
import DeleteConfirm from "../../components/DeleteConfirm";
import Toggle from "../../components/Toggle";
import { UserStatusContext } from "../../components/UserStatusProvider";

function UserForm(props: {
  disableModal: () => void;
  createNewUser: (
    email: string,
    password: string,
    fullName: string,
  ) => Promise<void>;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<CreateUserRequest>({
    resolver: zodResolver(createUserRequestSchema),
    defaultValues: { email: "", password: "", fullName: "" },
  });

  // Validation mirrors the backend CreateUserRequest constraints via the shared
  // createUserRequestSchema, so missing or invalid fields surface as inline
  // messages instead of an unhandled error.
  const onSubmit = async (data: CreateUserRequest) => {
    await props.createNewUser(data.email, data.password, data.fullName);
    props.disableModal();
  };

  return (
    <form
      method="post"
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
    >
      <div className="w-2xl rounded bg-slate-50 p-3 shadow dark:bg-slate-900">
        <h2 className="mb-4 text-lg font-semibold">Create New User</h2>
        <div className="mb-3 flex flex-col">
          <InputField
            id="email"
            label="Email"
            error={errors.email?.message}
            data-testid="email-input"
            {...register("email")}
          />
        </div>
        <div className="mb-3 flex flex-col">
          <InputField
            id="password"
            label="Password"
            type="passwordlike"
            error={errors.password?.message}
            data-testid="password-input"
            {...register("password")}
          />
        </div>
        <div className="mb-3 flex flex-col">
          <InputField
            id="fullName"
            label="Full Name"
            error={errors.fullName?.message}
            data-testid="name-input"
            {...register("fullName")}
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

  async function setActive(userId: string, active: boolean) {
    const response = await setUserActive(userId, active);
    if (isApiErrorResponse(response)) {
      setError(response.message);
      clearNotifications();
      return false;
    }
    setUsers(users.map((u) => (u.id === userId ? response : u)));
    setSuccess(
      active
        ? `${response.fullName || response.email} has been reactivated`
        : `${response.fullName || response.email} has been deactivated`,
    );
    clearNotifications();
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
    setActive,
    error,
    success,
    loading,
  };
};

// Shared by the header and the rows: name and email flex, the role combobox and the action
// button get the fixed room they need (the combobox is 8rem wide, 15rem from lg up).
const userGridClasses =
  "grid grid-cols-2 gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_12rem_6rem] lg:grid-cols-[minmax(0,1fr)_minmax(0,1.5fr)_19rem_6rem]";

const UserRow = (props: {
  user: UserResponse;
  roles: RoleResponse[];
  isCurrentUser: boolean;
  setRoles: (roles: RoleResponse[]) => Promise<boolean>;
  setActive: (active: boolean) => Promise<boolean>;
}) => {
  const canEditRoles = useHasPermission("user:edit_roles");
  const [confirmDeactivate, setConfirmDeactivate] = useState(false);
  const inactive = !props.user.active;
  // Deactivated accounts are grayed out; their roles stay editable so an admin can review
  // them before reactivating.
  const nameClasses = inactive
    ? "text-slate-400 dark:text-slate-500"
    : "text-slate-900 dark:text-slate-100";
  const emailClasses = inactive
    ? "text-slate-400 dark:text-slate-500"
    : "text-slate-600 dark:text-slate-400";

  return (
    <div
      className="flex flex-row border-b border-slate-200 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
      data-testid={`user-${props.user.email}`}
      data-inactive={inactive ? "true" : undefined}
    >
      <div className={`w-full px-6 py-4 ${userGridClasses}`}>
        <div className="flex min-w-0 flex-col justify-center gap-1">
          <div
            className={`truncate font-medium ${nameClasses}`}
            title={props.user.fullName ?? undefined}
          >
            {props.user.fullName}
          </div>
          {inactive && (
            <span
              className="w-fit rounded-full bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500 dark:bg-slate-800 dark:text-slate-400"
              data-testid="inactive-badge"
            >
              Inactive
            </span>
          )}
        </div>
        <div className="flex min-w-0 items-center">
          <div className={`truncate ${emailClasses}`} title={props.user.email}>
            {props.user.email}
          </div>
        </div>
        <div className="flex items-center">
          {canEditRoles ? (
            <RoleComboBox
              roles={props.user.roles}
              setRoles={props.setRoles}
              availableRoles={props.roles}
            />
          ) : (
            <div
              className={emailClasses}
              title="You lack permission to change user roles."
            >
              {props.user.roles.map((role) => role.name).join(", ")}
            </div>
          )}
        </div>
        <div className="flex items-center justify-end">
          {canEditRoles && !props.isCurrentUser && (
            <Button
              size="sm"
              variant={inactive ? "success" : "danger"}
              onClick={() =>
                inactive
                  ? void props.setActive(true)
                  : setConfirmDeactivate(true)
              }
              dataTestId={inactive ? "reactivate-user" : "deactivate-user"}
            >
              {inactive ? "Reactivate" : "Deactivate"}
            </Button>
          )}
        </div>
      </div>
      {confirmDeactivate && (
        <Modal setVisible={setConfirmDeactivate}>
          <DeleteConfirm
            title={`Deactivate ${props.user.fullName || props.user.email}?`}
            message="They are logged out immediately and can no longer sign in, and their license seat is freed. Their requests, reviews and other history stay, and you can reactivate the account at any time."
            onConfirm={async () => {
              await props.setActive(false);
              setConfirmDeactivate(false);
            }}
            onCancel={() => setConfirmDeactivate(false)}
          />
        </Modal>
      )}
    </div>
  );
};

const UserSettings = () => {
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const [showInactive, setShowInactive] = useState(false);
  const { users, createNewUser, error, success, setRoles, setActive, loading } =
    useUsers();
  const { userStatus } = useContext(UserStatusContext);
  const currentUserId = userStatus ? userStatus.id : undefined;
  const inactiveCount = users.filter((user) => !user.active).length;
  const visibleUsers = showInactive
    ? users
    : users.filter((user) => user.active);
  // The role list feeds the role combobox; without role:get the fetch would only
  // produce a 403 toast, so skip it entirely.
  const canListRoles = useHasPermission("role:get");
  const { roles } = useRoles(canListRoles);

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
          <div className="flex items-center gap-4">
            {inactiveCount > 0 && (
              <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-slate-400">
                <Toggle
                  active={showInactive}
                  onClick={() => setShowInactive(!showInactive)}
                />
                <button
                  type="button"
                  onClick={() => setShowInactive(!showInactive)}
                  data-testid="show-inactive-users"
                >
                  Show inactive users ({inactiveCount})
                </button>
              </div>
            )}
            <RequirePermission permission="user:create">
              <Button
                onClick={() => setShowCreateUserModal(true)}
                variant="primary"
                dataTestId="add-user-button"
              >
                Add User
              </Button>
            </RequirePermission>
          </div>
        </div>
      </div>

      {/* User list */}
      {visibleUsers.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <p className="text-slate-500 dark:text-slate-400">
            No users found. Create one to get started.
          </p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow dark:border-slate-700 dark:bg-slate-900">
          {/* Table header */}
          <div className="bg-slate-50 dark:bg-slate-800">
            <div className={`px-6 py-3 ${userGridClasses}`}>
              <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                Name
              </div>
              <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                Email
              </div>
              <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                Roles
              </div>
              <div></div>
            </div>
          </div>

          {/* User rows */}
          <div>
            {visibleUsers.map((user) => (
              <UserRow
                key={user.id}
                user={user}
                roles={roles}
                isCurrentUser={user.id === currentUserId}
                setRoles={(roles) => {
                  return setRoles(user.id, roles);
                }}
                setActive={(active) => setActive(user.id, active)}
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
