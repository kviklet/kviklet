import { useEffect, useState } from "react";
import { RoleResponse, getRole } from "../api/RoleApi";
import { z } from "zod";

const UserPolicySchema = z.object({
  read: z.boolean(),
  create: z.boolean(),
  editSelf: z.boolean(),
  delete: z.boolean(),
});

const RolePolicy = z.object({
  read: z.boolean(),
  edit: z.boolean(),
});

const ConnectionPolicy = z.object({
  selector: z.string(),
  read: z.boolean(),
  create: z.boolean(),
  edit: z.boolean(),
  execution_request_get: z.boolean(),
  execution_request_edit: z.boolean(),
  execution_request_execute: z.boolean(),
});

const RoleSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
  userPolicy: UserPolicySchema,
  rolePolicy: RolePolicy,
  connectionPolicies: z.array(ConnectionPolicy),
});

type Role = z.infer<typeof RoleSchema>;

const useRole = (id: string) => {
  const [role, setRole] = useState<RoleResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  async function request() {
    setLoading(true);
    const role = await getRole(id);
    setRole(role);
    setLoading(false);
  }

  useEffect(() => {
    void request();
  }, [id]);

  return {
    loading,
    role,
  };
};

const transformRole = (role: RoleResponse): Role => {
  const userPolicy = {
    read: false,
    create: false,
    editSelf: false,
    delete: false,
  };

  const rolePolicy = {
    read: false,
    edit: false,
  };

  const connectionPoliciesMap: {
    [key: string]: z.infer<typeof ConnectionPolicy>;
  } = {};

  role.policies.forEach((policy) => {
    const [resourceType, resourceAction] = policy.action.split(":");

    switch (resourceType) {
      case "user":
        if (resourceAction === "get") userPolicy.read = true;
        if (resourceAction === "create") userPolicy.create = true;
        if (resourceAction === "edit") userPolicy.editSelf = true;
        if (resourceAction === "delete") userPolicy.delete = true;
        break;

      case "role":
        if (resourceAction === "get") rolePolicy.read = true;
        if (resourceAction === "edit") rolePolicy.edit = true;
        break;

      case "datasource_connection":
      case "execution_request": {
        if (!connectionPoliciesMap[policy.resource]) {
          connectionPoliciesMap[policy.resource] = {
            selector: policy.resource,
            read: false,
            create: false,
            edit: false,
            execution_request_get: false,
            execution_request_edit: false,
            execution_request_execute: false,
          };
        }

        const connectionPolicy = connectionPoliciesMap[policy.resource];
        if (resourceAction === "get") connectionPolicy.read = true;
        if (resourceAction === "create") connectionPolicy.create = true;
        if (resourceAction === "edit") connectionPolicy.edit = true;
        if (resourceType === "execution_request" && resourceAction === "get")
          connectionPolicy.execution_request_get = true;
        if (resourceType === "execution_request" && resourceAction === "edit")
          connectionPolicy.execution_request_edit = true;
        if (
          resourceType === "execution_request" &&
          resourceAction === "execute"
        )
          connectionPolicy.execution_request_execute = true;
        break;
      }

      default:
        break;
    }
  });

  return {
    name: role.name,
    description: role.description,
    userPolicy,
    rolePolicy,
    connectionPolicies: Object.values(connectionPoliciesMap),
  };
};

export {
  useRole,
  transformRole,
  RoleSchema,
  UserPolicySchema,
  RolePolicy,
  ConnectionPolicy,
};

export type { Role };
