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

const AllConnectionPolicy = ConnectionPolicy.omit({ selector: true });

const emptyConnectionFlags = (): z.infer<typeof AllConnectionPolicy> => ({
  execution_request_read: false,
  execution_request_write: false,
  execution_request_review: false,
});

const RoleSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  isAdmin: z.boolean(),
  bypassApproval: z.boolean(),
  userPolicy: UserPolicySchema,
  rolePolicy: RolePolicy,
  allConnectionPolicy: AllConnectionPolicy,
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

const emptyConnectionPolicy = (
  selector: string,
): z.infer<typeof ConnectionPolicy> => ({
  selector,
  ...emptyConnectionFlags(),
});

const appendConnectionPolicies = (
  policies: PolicyUpdatePayload[],
  selector: string,
  flags: z.infer<typeof AllConnectionPolicy>,
) => {
  const hasAny =
    flags.execution_request_read ||
    flags.execution_request_write ||
    flags.execution_request_review;

  if (!hasAny && selector !== "*") {
    policies.push({
      action: "datasource_connection:get",
      resource: selector,
    });
    return;
  }
  if (!hasAny) {
    return;
  }

  policies.push({
    action: "datasource_connection:get",
    resource: selector,
  });
  policies.push({
    action: "execution_request:get",
    resource: selector,
  });
  if (flags.execution_request_review) {
    policies.push({
      action: "execution_request:review",
      resource: selector,
    });
  }
  if (flags.execution_request_write) {
    policies.push({
      action: "execution_request:edit",
      resource: selector,
    });
    policies.push({
      action: "execution_request:execute",
      resource: selector,
    });
  }
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
        if (resourceAction === "*" && policy.resource === "*") {
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
          connectionPoliciesMap[policy.resource] = emptyConnectionPolicy(
            policy.resource,
          );
        }
        break;
      }
      case "execution_request": {
        if (!connectionPoliciesMap[policy.resource]) {
          connectionPoliciesMap[policy.resource] = emptyConnectionPolicy(
            policy.resource,
          );
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

  const allConnectionPolicy =
    connectionPoliciesMap["*"] ?? emptyConnectionPolicy("*");
  delete connectionPoliciesMap["*"];

  return {
    id: role.id,
    name: role.name,
    description: role.description,
    isAdmin,
    bypassApproval: role.bypassApproval ?? false,
    userPolicy,
    rolePolicy,
    allConnectionPolicy: {
      execution_request_read: allConnectionPolicy.execution_request_read,
      execution_request_write: allConnectionPolicy.execution_request_write,
      execution_request_review: allConnectionPolicy.execution_request_review,
    },
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
      resource: "*",
    });

    return {
      id: role.id,
      name: role.name,
      description: role.description,
      policies,
      bypassApproval: role.bypassApproval,
    };
  }

  if (role.userPolicy.read) {
    policies.push({
      action: "user:get",
      resource: "*",
    });
  }
  if (role.userPolicy.create) {
    policies.push({
      action: "user:create",
      resource: "*",
    });
  }
  if (role.userPolicy.editSelf) {
    policies.push({
      action: "user:edit",
      resource: "*",
    });
  }

  if (role.rolePolicy.read) {
    policies.push({
      action: "role:get",
      resource: "*",
    });
  }

  appendConnectionPolicies(policies, "*", role.allConnectionPolicy);

  role.connectionPolicies.forEach((policy) => {
    if (policy.selector === "*") {
      return;
    }
    appendConnectionPolicies(policies, policy.selector, policy);
  });

  return {
    id: role.id,
    name: role.name,
    description: role.description,
    policies,
    bypassApproval: role.bypassApproval,
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
  AllConnectionPolicy,
};

export type { Role };
