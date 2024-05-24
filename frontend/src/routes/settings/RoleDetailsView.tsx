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

interface RoleDetailsParams {
  roleId: string;
}

export default function RoleDetailsView() {
  const params = useParams() as unknown as RoleDetailsParams;
  const roleId = params.roleId;

  const { loading, role, reloadRole } = useRole(roleId);

  const submit = async (data: Role) => {
    await patchRole(data.id, transformToPayload(data));
    await reloadRole();
  };

  return (
    <div>
      <div className="mb-3 border-b border-slate-300 dark:border-slate-700">
        <h1 className="text-xl">{role && role.name}</h1>
      </div>
      {loading && <Spinner></Spinner>}
      {role && (
        <RoleForm role={transformRole(role)} onSubmit={submit}></RoleForm>
      )}
      <div className="mx-auto max-w-7xl"></div>
    </div>
  );
}
