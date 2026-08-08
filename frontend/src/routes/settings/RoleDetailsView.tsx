import { useParams } from "react-router-dom";
import {
  Role,
  transformRole,
  transformToPayload,
  useRole,
} from "../../hooks/roles";
import RoleForm from "./RoleForm";
import Spinner from "../../components/Spinner";
import { patchRole } from "../../api/RoleApi";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import { useHasPermission } from "../../hooks/permissions";
import ReadOnlyBadge from "../../components/ReadOnlyBadge";

interface RoleDetailsParams {
  roleId: string;
}

export default function RoleDetailsView() {
  const params = useParams() as unknown as RoleDetailsParams;
  const roleId = params.roleId;

  const { loading, role, reloadRole } = useRole(roleId);
  const canEditRoles = useHasPermission("role:edit");
  const { addNotification } = useNotification();

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
    addNotification({
      title: "Role updated",
      text: "The role has been saved",
      type: "info",
    });
    await reloadRole();
  };

  return (
    <div>
      <div className="mb-3 flex items-center gap-2 border-b border-slate-300 pb-1 dark:border-slate-700">
        <h1 className="text-xl">{role && role.name}</h1>
        {!canEditRoles && (
          <ReadOnlyBadge tooltip="You lack the permission to change this role." />
        )}
      </div>
      {loading && <Spinner size="lg" page />}
      {role && (
        <fieldset disabled={!canEditRoles}>
          <RoleForm
            role={transformRole(role)}
            onSubmit={submit}
            readOnly={!canEditRoles}
          ></RoleForm>
        </fieldset>
      )}
      <div className="mx-auto max-w-7xl"></div>
    </div>
  );
}
