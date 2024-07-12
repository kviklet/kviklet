import { useEffect, useState } from "react";
import {
  PolicyUpdatePayload,
  RoleResponse,
  RoleUpdatePayload,
  getRole,
} from "../api/RoleApi";
import { z } from "zod";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "./useNotification";

const UserPolicySchema = z.object({
  read: z.boolean(),
  create: z.boolean(),
  editSelf: z.boolean(),
});

const RolePolicy = z.object({
  read: z.boolean(),
});

const ConnectionPolicy = z.object({
  selector: z.string(),
  execution_request_read: z.boolean(),
  execution_request_write: z.boolean(),
  execution_request_review: z.boolean(),
});

const RoleSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  isAdmin: z.boolean(),
  userPolicy: UserPolicySchema,
  rolePolicy: RolePolicy,
  connectionPolicies: z.array(ConnectionPolicy),
});

type Role = z.infer<typeof RoleSchema>;

const useRole = (id: string) => {
  const [role, setRole] = useState<RoleResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const { addNotification } = useNotification();

  async function reloadRole() {
    setLoading(true);
    const response = await getRole(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to load role",
        text: response.message,
        type: "error",
      });
    } else {
      setRole(response);
    }
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
  };

  const rolePolicy = {
    read: false,
  };

  const connectionPoliciesMap: {
    [key: string]: z.infer<typeof ConnectionPolicy>;
  } = {};

  let isAdmin = false;

  role.policies.forEach((policy) => {
    const [resourceType, resourceAction] = policy.action.split(":");

    switch (resourceType) {
      case "*":
        if (
          resourceAction === "*" &&
          policy.effect === "ALLOW" &&
          policy.resource === "*"
        ) {
          isAdmin = true;
        }
        break;
      case "user":
        if (resourceAction === "get") userPolicy.read = true;
        if (resourceAction === "create") userPolicy.create = true;
        if (resourceAction === "edit") userPolicy.editSelf = true;
        break;

      case "role":
        if (resourceAction === "get") rolePolicy.read = true;
        break;

      case "datasource_connection": {
        if (!connectionPoliciesMap[policy.resource]) {
          connectionPoliciesMap[policy.resource] = {
            selector: policy.resource,
            execution_request_read: false,
            execution_request_write: false,
            execution_request_review: false,
          };
        }
        break;
      }
      case "execution_request": {
        if (!connectionPoliciesMap[policy.resource]) {
          connectionPoliciesMap[policy.resource] = {
            selector: policy.resource,
            execution_request_read: false,
            execution_request_write: false,
            execution_request_review: false,
          };
        }

        const connectionPolicy = connectionPoliciesMap[policy.resource];

        if (resourceAction === "get")
          connectionPolicy.execution_request_read = true;
        if (resourceAction === "edit")
          connectionPolicy.execution_request_write = true;
        if (resourceAction === "review")
          connectionPolicy.execution_request_review = true;
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
    isAdmin,
    userPolicy,
    rolePolicy,
    connectionPolicies: Object.values(connectionPoliciesMap),
  };
};

const transformToPayload = (
  role: z.infer<typeof RoleSchema>,
): RoleUpdatePayload => {
  const policies: PolicyUpdatePayload[] = [];

  if (role.isAdmin) {
    policies.push({
      action: "*:*",
      effect: "ALLOW",
      resource: "*",
    });

    return {
      id: role.id,
      name: role.name,
      description: role.description,
      policies,
    };
  }

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

  if (role.rolePolicy.read) {
    policies.push({
      action: "role:get",
      effect: "ALLOW",
      resource: "*",
    });
  }

  role.connectionPolicies.forEach((policy) => {
    policies.push({
      action: "datasource_connection:get",
      effect: "ALLOW",
      resource: policy.selector,
    });
    if (
      policy.execution_request_read ||
      policy.execution_request_write ||
      policy.execution_request_review
    ) {
      policies.push({
        action: "execution_request:get",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }

    if (policy.execution_request_review) {
      policies.push({
        action: "execution_request:review",
        effect: "ALLOW",
        resource: policy.selector,
      });
    }

    if (policy.execution_request_write) {
      policies.push({
        action: "execution_request:edit",
        effect: "ALLOW",
        resource: policy.selector,
      });
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
