import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  Role,
  transformRole,
  transformToPayload,
  useRole,
} from "../../hooks/roles";
import RoleForm from "./RoleForm";
import Spinner from "../../components/Spinner";
import { patchRole, removeRole } from "../../api/RoleApi";
import { fetchUsers, UserResponse } from "../../api/UserApi";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import { useHasPermission } from "../../hooks/permissions";
import ReadOnlyBadge from "../../components/ReadOnlyBadge";
import Breadcrumbs from "../../components/Breadcrumbs";
import Button from "../../components/Button";
import Modal from "../../components/Modal";
import DeleteConfirm from "../../components/DeleteConfirm";
import { FormSection } from "../../components/FormSection";

const rolesPath = "/settings/roles";
const shownMembers = 8;

function Members(props: { roleId: string; isDefault: boolean }) {
  const [members, setMembers] = useState<UserResponse[] | null>(null);

  useEffect(() => {
    const load = async () => {
      const response = await fetchUsers();
      if (!isApiErrorResponse(response)) {
        setMembers(
          response.users.filter((user) =>
            user.roles.some((role) => role.id === props.roleId),
          ),
        );
      }
    };
    void load();
  }, [props.roleId]);

  if (members === null) {
    return null;
  }
  const hidden = members.length - shownMembers;

  return (
    <FormSection
      title="Members"
      description={
        props.isDefault ? (
          "Every user has this role."
        ) : (
          <>
            Roles are assigned on the{" "}
            <Link
              to="/settings/users"
              className="text-indigo-700 hover:underline dark:text-indigo-300"
            >
              Users page
            </Link>
            .
          </>
        )
      }
      testId="role-members"
    >
      {members.length === 0 ? (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No user has this role yet.
        </p>
      ) : (
        <ul className="flex flex-col gap-2 text-sm">
          {members.slice(0, shownMembers).map((user) => (
            <li key={user.id} className="flex flex-wrap items-baseline gap-x-2">
              <span className="font-medium text-slate-900 dark:text-slate-50">
                {user.fullName ?? user.email}
              </span>
              {user.fullName && (
                <span className="text-slate-500 dark:text-slate-400">
                  {user.email}
                </span>
              )}
              {!user.active && (
                <span className="text-xs text-slate-500 dark:text-slate-400">
                  (deactivated)
                </span>
              )}
            </li>
          ))}
          {hidden > 0 && (
            <li className="text-slate-500 dark:text-slate-400">
              and {hidden} more
            </li>
          )}
        </ul>
      )}
    </FormSection>
  );
}

export default function RoleDetailsView() {
  const { roleId } = useParams() as { roleId: string };
  const { loading, role, setRole } = useRole(roleId);
  const canEditRoles = useHasPermission("role:edit");
  const canListUsers = useHasPermission("user:get");
  const { addNotification } = useNotification();
  const navigate = useNavigate();
  const [showDeleteModal, setShowDeleteModal] = useState(false);

  // Stable while the stored role is unchanged, so the form only resets after a save.
  const formRole = useMemo(() => (role ? transformRole(role) : null), [role]);

  const submit = async (data: Role) => {
    const response = await patchRole(data.id, transformToPayload(data));
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to update role",
        text: response.message,
        type: "error",
      });
      return;
    }
    setRole(response);
    addNotification({
      title: "Role updated",
      text: "The role has been saved",
      type: "info",
    });
  };

  const handleDeleteConfirm = async () => {
    const response = await removeRole(roleId);
    setShowDeleteModal(false);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to delete role",
        text: response.message,
        type: "error",
      });
      return;
    }
    addNotification({
      title: "Role deleted",
      text: "The role has been successfully deleted",
      type: "info",
    });
    void navigate(rolesPath);
  };

  if (loading) {
    return <Spinner size="lg" page />;
  }
  if (!role || !formRole) {
    return <div>Role not found</div>;
  }

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6 flex items-center gap-3">
        <Breadcrumbs
          items={[{ label: "Roles", to: rolesPath }, { label: role.name }]}
        />
        {role.isDefault && (
          <span className="rounded border border-slate-300 px-2 py-0.5 text-xs font-medium text-slate-600 dark:border-slate-700 dark:text-slate-300">
            System
          </span>
        )}
        {!canEditRoles && (
          <ReadOnlyBadge tooltip="You lack the permission to change this role." />
        )}
      </div>
      <div className="flex max-w-3xl flex-col gap-6">
        <RoleForm role={formRole} onSubmit={submit} readOnly={!canEditRoles} />
        {canListUsers && (
          <Members roleId={role.id} isDefault={role.isDefault} />
        )}
        {canEditRoles && !role.isDefault && (
          <section className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-red-200 bg-white p-6 dark:border-red-900 dark:bg-slate-900">
            <div className="min-w-0 flex-1 basis-80">
              <h2 className="text-base font-semibold text-slate-900 dark:text-slate-50">
                Delete this role
              </h2>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                Its members lose the permissions this role gives them.
              </p>
            </div>
            <Button
              onClick={() => setShowDeleteModal(true)}
              variant="danger"
              dataTestId="delete-role-button"
            >
              Delete role
            </Button>
          </section>
        )}
      </div>
      {showDeleteModal && (
        <Modal setVisible={setShowDeleteModal}>
          <DeleteConfirm
            title="Delete role"
            message={`Are you sure you want to delete the role "${role.name}"?`}
            onConfirm={handleDeleteConfirm}
            onCancel={() => setShowDeleteModal(false)}
          />
        </Modal>
      )}
    </div>
  );
}
