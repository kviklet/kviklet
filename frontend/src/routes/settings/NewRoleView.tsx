import { useNavigate } from "react-router-dom";
import { Role, transformToPayload } from "../../hooks/roles";
import RoleForm from "./RoleForm";
import { createRole } from "../../api/RoleApi";
import { isApiErrorResponse } from "../../api/Errors";
import useNotification from "../../hooks/useNotification";
import Breadcrumbs from "../../components/Breadcrumbs";

const rolesPath = "/settings/roles";

const newRole: Role = {
  id: "",
  name: "",
  description: "",
  isAdmin: false,
  userPolicy: { read: false, create: false, editSelf: false },
  rolePolicy: { read: false },
  connectionPolicies: [],
};

export default function NewRoleView() {
  const navigate = useNavigate();
  const { addNotification } = useNotification();

  const submit = async (data: Role) => {
    const payload = transformToPayload(data);
    const response = await createRole({
      name: payload.name,
      description: payload.description,
      policies: payload.policies,
    });
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to create role",
        text: response.message,
        type: "error",
      });
      return;
    }
    void navigate(rolesPath);
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <Breadcrumbs
        className="mb-6"
        items={[{ label: "Roles", to: rolesPath }, { label: "New role" }]}
      />
      <div className="max-w-3xl">
        <RoleForm role={newRole} onSubmit={submit} submitLabel="Create role" />
      </div>
    </div>
  );
}
