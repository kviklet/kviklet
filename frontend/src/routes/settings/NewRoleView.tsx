import { useNavigate } from "react-router-dom";
import { Role, transformToPayload } from "../../hooks/roles";
import RoleForm from "./RoleForm";
import { createRole } from "../../api/RoleApi";
import { useState } from "react";

export default function RoleDetailsView() {
  const defaultRole: Role = {
    id: "",
    name: "",
    description: "",
    isAdmin: false,
    userPolicy: {
      read: false,
      create: false,
      editSelf: false,
    },
    rolePolicy: {
      read: false,
    },
    connectionPolicies: [],
  };

  const [role] = useState<Role>(defaultRole);

  const navigate = useNavigate();

  const submit = async (data: Role) => {
    const transformedRole = transformToPayload(data);
    await createRole({
      name: transformedRole.name,
      description: transformedRole.description,
      policies: transformedRole.policies,
    });
    navigate("/settings/roles");
  };

  return (
    <div>
      <div className="mb-3 border-b border-slate-300 dark:border-slate-700">
        <h1 className="text-xl">{role && role.name}</h1>
      </div>
      {role && <RoleForm role={role} onSubmit={submit}></RoleForm>}
      <div className="mx-auto max-w-7xl"></div>
    </div>
  );
}
