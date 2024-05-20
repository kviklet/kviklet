import { useParams } from "react-router-dom";
import { transformRole, useRole } from "../../hooks/roles";
import { z } from "zod";
import RoleForm from "./RoleForm";
import Spinner from "../../components/Spinner";

interface RoleDetailsParams {
  roleId: string;
}

export default function RoleDetailsView() {
  const params = useParams() as unknown as RoleDetailsParams;
  const roleId = params.roleId;

  const { loading, role } = useRole(roleId);

  return (
    <div>
      <div className="mb-3 border-b border-slate-300 dark:border-slate-700">
        <h1 className="m-5 mx-auto w-3/4 pl-1.5 text-xl">RoleDetailsView</h1>
        <h2>{role && role.id}</h2>
      </div>
      {loading && <Spinner></Spinner>}
      {role && <RoleForm role={transformRole(role)}></RoleForm>}
      <div className="mx-auto max-w-7xl"></div>
    </div>
  );
}
