import { useEffect, useState } from "react";
import {
  PolicyUpdatePayload,
  RoleResponse,
  RoleUpdatePayload,
  getRole,
} from "../api/RoleApi";
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
  id: z.string(),
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

  async function reloadRole() {
    setLoading(true);
    const role = await getRole(id);
    setRole(role);
    setLoading(false);
  }

  useEffect(() => {
    void reloadRole();
  }, [id]);

  return {
    loading,
    role,
    reloadRole,
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
    id: role.id,
    name: role.name,
    description: role.description,
    userPolicy,
    rolePolicy,
    connectionPolicies: Object.values(connectionPoliciesMap),
  };
};

const transformToPayload = (
  role: z.infer<typeof RoleSchema>,
): RoleUpdatePayload => {
  const policies: PolicyUpdatePayload[] = [];

  if (role.userPolicy.read) {
    policies.push({
      action: "user:get",
      effect: "ALLOW",
      resource: "*",
    });
  }
  if (role.userPolicy.create) {
    policies.push({
      action: "user:create",
      effect: "ALLOW",
      resource: "*",
    });
  }
  if (role.userPolicy.editSelf) {
    policies.push({
      action: "user:edit",
      effect: "ALLOW",
      resource: "*",
    });
  }
  if (role.userPolicy.delete) {
    policies.push({
      action: "user:delete",
      effect: "ALLOW",
      resource: "*",
    });
  }

  // Transform rolePolicy
  if (role.rolePolicy.read) {
    policies.push({
      action: "role:get",
      effect: "ALLOW",
      resource: "*",
    });
  }
  if (role.rolePolicy.edit) {
    policies.push({
      action: "role:edit",
      effect: "ALLOW",
      resource: "*",
    });
  }

  role.connectionPolicies.forEach((policy) => {
    if (policy.read) {
      policies.push({
        action: "datasource_connection:get",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }
    if (policy.create) {
      policies.push({
        action: "datasource_connection:create",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }
    if (policy.edit) {
      policies.push({
        action: "datasource_connection:edit",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }
    if (policy.execution_request_get) {
      policies.push({
        action: "execution_request:get",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }
    if (policy.execution_request_edit) {
      policies.push({
        action: "execution_request:edit",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }
    if (policy.execution_request_execute) {
      policies.push({
        action: "execution_request:execute",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }
  });

  return {
    id: role.id,
    name: role.name,
    description: role.description,
    policies,
  };
};

export {
  useRole,
  transformRole,
  transformToPayload,
  RoleSchema,
  UserPolicySchema,
  RolePolicy,
  ConnectionPolicy,
};

export type { Role };
