import React, { useContext, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import Button from "../../components/Button";
import Modal from "../../components/Modal";
import OncallGrantModal, {
  OncallGrantSubmit,
} from "../../components/OncallGrantModal";
import {
  CreateUserRequest,
  OncallGrantKind,
  OncallGrantResponse,
  UserResponse,
  approveOncallGrant,
  createUser,
  createUserRequestSchema,
  fetchUsers,
  revokeOncallGrant,
  startOncallGrant,
  updateUser,
} from "../../api/UserApi";
import InputField from "../../components/InputField";
import { useRoles } from "./RolesSettings";
import { RoleResponse } from "../../api/RoleApi";
import { isApiErrorResponse } from "../../api/Errors";
import { Error, Success } from "../../components/Alert";
import RoleComboBox from "./RoleComboBox";
import RequirePermission from "../../components/RequirePermission";
import { useHasPermission } from "../../hooks/permissions";
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
  const userContext = useContext(UserStatusContext);

  function clearNotifications() {
    setTimeout(() => {
      setError("");
      setSuccess("");
    }, 5000);
  }

  async function reloadUsers() {
    const response = await fetchUsers();
    if (isApiErrorResponse(response)) {
      setError(response.message);
    } else {
      setUsers(response.users);
    }
  }

  useEffect(() => {
    async function request() {
      await reloadUsers();
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

  async function startGrant(
    userId: string,
    kind: OncallGrantKind,
    durationMinutes: number,
    reason: string,
    bypassApproval: boolean,
  ) {
    const response = await startOncallGrant(userId, {
      kind,
      durationMinutes,
      reason: reason.trim() || undefined,
      bypassApproval,
    });
    if (isApiErrorResponse(response)) {
      setError(response.message);
      clearNotifications();
      return false;
    }
    await reloadUsers();
    await userContext.refreshState();
    setSuccess(
      kind === "OUTAGE" ? "Outage access started" : "On-call access started",
    );
    clearNotifications();
    return true;
  }

  async function approveGrant(userId: string) {
    const response = await approveOncallGrant(userId);
    if (isApiErrorResponse(response)) {
      setError(response.message);
      clearNotifications();
      return false;
    }
    await reloadUsers();
    await userContext.refreshState();
    setSuccess("On-call / outage request approved");
    clearNotifications();
    return true;
  }

  async function endGrant(userId: string) {
    const response = await revokeOncallGrant(userId);
    if (response !== null) {
      setError(response.message);
      clearNotifications();
      return false;
    }
    await reloadUsers();
    await userContext.refreshState();
    setSuccess("On-call / outage access ended");
    clearNotifications();
    return true;
  }

  return {
    users,
    createNewUser,
    setRoles,
    startGrant,
    approveGrant,
    endGrant,
    error,
    success,
    loading,
  };
};

function grantLabel(grant: OncallGrantResponse): string {
  return grant.kind === "OUTAGE" ? "Outage" : "On-call";
}

const UserRow = (props: {
  user: UserResponse;
  roles: RoleResponse[];
  setRoles: (roles: RoleResponse[]) => Promise<boolean>;
  startGrant: OncallGrantSubmit;
  approveGrant: (userId: string) => Promise<boolean>;
  endGrant: (userId: string) => Promise<boolean>;
}) => {
  const canEditRoles = useHasPermission("user:edit_roles");
  const userContext = useContext(UserStatusContext);
  const canManageOncall =
    userContext.userStatus && userContext.userStatus !== false
      ? Boolean(userContext.userStatus.canManageOncall)
      : false;
  const [showStartModal, setShowStartModal] = useState(false);
  const activeGrant = props.user.activeOncallGrant ?? null;
  const pendingGrant = props.user.pendingOncallGrant ?? null;

  return (
    <tr
      className="border-b border-slate-200 last:border-0 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
      data-testid={`user-${props.user.email}`}
    >
      <td className="max-w-[10rem] truncate px-4 py-3 font-medium text-slate-900 dark:text-slate-100">
        {props.user.fullName}
      </td>
      <td
        className="max-w-[16rem] truncate px-4 py-3 text-slate-600 dark:text-slate-400"
        title={props.user.email}
      >
        {props.user.email}
      </td>
      <td className="px-4 py-3">
        {canEditRoles ? (
          <RoleComboBox
            roles={props.user.roles}
            setRoles={props.setRoles}
            availableRoles={props.roles}
          />
        ) : (
          <div
            className="truncate text-slate-600 dark:text-slate-400"
            title={props.user.roles.map((role) => role.name).join(", ")}
          >
            {props.user.roles.map((role) => role.name).join(", ")}
          </div>
        )}
      </td>
      <td className="px-4 py-3">
        <div className="flex flex-wrap items-center justify-end gap-2">
          {activeGrant ? (
            <>
              <span
                className="text-xs font-medium text-amber-700 dark:text-amber-300"
                data-testid={`oncall-active-${props.user.email}`}
              >
                {grantLabel(activeGrant)}
              </span>
              {canManageOncall && (
                <Button
                  size="sm"
                  variant="danger"
                  onClick={() => void props.endGrant(props.user.id)}
                  dataTestId={`end-oncall-${props.user.email}`}
                >
                  End
                </Button>
              )}
            </>
          ) : pendingGrant ? (
            <>
              <span
                className="text-xs font-medium text-sky-700 dark:text-sky-300"
                data-testid={`oncall-pending-${props.user.email}`}
              >
                Pending {grantLabel(pendingGrant)}
              </span>
              {canManageOncall && (
                <>
                  <Button
                    size="sm"
                    variant="success"
                    onClick={() => void props.approveGrant(props.user.id)}
                    dataTestId={`approve-oncall-${props.user.email}`}
                  >
                    Approve
                  </Button>
                  <Button
                    size="sm"
                    variant="danger"
                    onClick={() => void props.endGrant(props.user.id)}
                    dataTestId={`deny-oncall-${props.user.email}`}
                  >
                    Deny
                  </Button>
                </>
              )}
            </>
          ) : (
            canManageOncall && (
              <Button
                size="sm"
                onClick={() => setShowStartModal(true)}
                dataTestId={`start-oncall-${props.user.email}`}
              >
                Start
              </Button>
            )
          )}
        </div>
        {showStartModal && (
          <Modal setVisible={setShowStartModal}>
            <OncallGrantModal
              userId={props.user.id}
              userLabel={props.user.fullName || props.user.email}
              mode="start"
              disableModal={() => setShowStartModal(false)}
              onSubmit={props.startGrant}
            />
          </Modal>
        )}
      </td>
    </tr>
  );
};

const UserSettings = () => {
  const [showCreateUserModal, setShowCreateUserModal] = useState(false);
  const {
    users,
    createNewUser,
    error,
    success,
    setRoles,
    startGrant,
    approveGrant,
    endGrant,
    loading,
  } = useUsers();
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

      <div className="mb-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
            Users
          </h2>
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

      {users.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <p className="text-slate-500 dark:text-slate-400">
            No users found. Create one to get started.
          </p>
        </div>
      ) : (
        <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow dark:border-slate-700 dark:bg-slate-900">
          <table className="w-full min-w-[56rem] table-fixed">
            <thead className="bg-slate-50 dark:bg-slate-800">
              <tr>
                <th className="w-[18%] px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                  Name
                </th>
                <th className="w-[28%] px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                  Email
                </th>
                <th className="w-[28%] px-4 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                  Roles
                </th>
                <th className="w-[26%] px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                  On-call / outage
                </th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <UserRow
                  key={user.id}
                  user={user}
                  roles={roles}
                  setRoles={(roles) => {
                    return setRoles(user.id, roles);
                  }}
                  startGrant={startGrant}
                  approveGrant={approveGrant}
                  endGrant={endGrant}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}

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
