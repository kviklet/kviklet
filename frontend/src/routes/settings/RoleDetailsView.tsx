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
      <div className="mb-3 border-b border-slate-300 dark:border-slate-700">
        <h1 className="text-xl">{role && role.name}</h1>
      </div>
      {loading && <Spinner size="lg" page />}
      {!canEditRoles && (
        <p className="mb-3 text-sm text-slate-500 dark:text-slate-400">
          You can view this role but lack the permission to change it.
        </p>
      )}
      {role && (
        <fieldset
          disabled={!canEditRoles}
          title={
            canEditRoles
              ? undefined
              : "You lack permission to edit roles. Ask an administrator."
          }
        >
          <RoleForm role={transformRole(role)} onSubmit={submit}></RoleForm>
        </fieldset>
      )}
      <div className="mx-auto max-w-7xl"></div>
    </div>
  );
}
