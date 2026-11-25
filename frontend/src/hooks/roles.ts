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
  selector: z.string().trim().min(1, "Required"),
  execution_request_read: z.boolean(),
  execution_request_write: z.boolean(),
  execution_request_review: z.boolean(),
});

const OtherPolicy = z.object({
  action: z.string(),
  resource: z.string(),
});

const RoleSchema = z.object({
  id: z.string(),
  name: z.string().trim().min(1, "Required"),
  description: z.string().nullable(),
  isAdmin: z.boolean(),
  userPolicy: UserPolicySchema,
  rolePolicy: RolePolicy,
  connectionPolicies: z.array(ConnectionPolicy),
  // Policies the form has no control for, e.g. ones set through the API. They are
  // carried through unchanged so that saving a role never drops them.
  otherPolicies: z.array(OtherPolicy),
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
    setRole,
    reloadRole,
  };
};

const newConnectionPolicy = (
  selector: string,
): z.infer<typeof ConnectionPolicy> => ({
  selector,
  execution_request_read: false,
  execution_request_write: false,
  execution_request_review: false,
});

const transformRole = (role: RoleResponse): Role => {
  const userPolicy = { read: false, create: false, editSelf: false };
  const rolePolicy = { read: false };
  const connectionPolicies = new Map<
    string,
    z.infer<typeof ConnectionPolicy>
  >();
  const otherPolicies: z.infer<typeof OtherPolicy>[] = [];
  let isAdmin = false;

  const connectionPolicy = (selector: string) => {
    if (!connectionPolicies.has(selector)) {
      connectionPolicies.set(selector, newConnectionPolicy(selector));
    }
    return connectionPolicies.get(selector)!;
  };
  // Writing covers editing and executing; execute is only expressed together with edit.
  const editSelectors = new Set(
    role.policies
      .filter((policy) => policy.action === "execution_request:edit")
      .map((policy) => policy.resource),
  );

  role.policies.forEach(({ action, resource }) => {
    const global = resource === "*";
    if (action === "*:*" && global) {
      isAdmin = true;
    } else if (action === "user:get" && global) {
      userPolicy.read = true;
    } else if (action === "user:create" && global) {
      userPolicy.create = true;
    } else if (action === "user:edit" && global) {
      userPolicy.editSelf = true;
    } else if (action === "role:get" && global) {
      rolePolicy.read = true;
    } else if (action === "datasource_connection:get") {
      connectionPolicy(resource);
    } else if (action === "execution_request:get") {
      connectionPolicy(resource).execution_request_read = true;
    } else if (action === "execution_request:edit") {
      connectionPolicy(resource).execution_request_write = true;
    } else if (action === "execution_request:review") {
      connectionPolicy(resource).execution_request_review = true;
    } else if (
      action === "execution_request:execute" &&
      editSelectors.has(resource)
    ) {
      // Covered by the write flag of that selector.
    } else {
      otherPolicies.push({ action, resource });
    }
  });

  return {
    id: role.id,
    name: role.name,
    description: role.description,
    isAdmin,
    userPolicy,
    rolePolicy,
    connectionPolicies: Array.from(connectionPolicies.values()),
    otherPolicies,
  };
};

const transformToPayload = (
  role: z.infer<typeof RoleSchema>,
): RoleUpdatePayload => {
  const policies: PolicyUpdatePayload[] = [];

  if (role.isAdmin) {
    policies.push({ action: "*:*", resource: "*" });
    policies.push(...role.otherPolicies);
    return {
      id: role.id,
      name: role.name,
      description: role.description,
      policies,
    };
  }

  if (role.userPolicy.read) {
    policies.push({ action: "user:get", resource: "*" });
  }
  if (role.userPolicy.create) {
    policies.push({ action: "user:create", resource: "*" });
  }
  if (role.userPolicy.editSelf) {
    policies.push({ action: "user:edit", resource: "*" });
  }
  if (role.rolePolicy.read) {
    policies.push({ action: "role:get", resource: "*" });
  }

  role.connectionPolicies.forEach((policy) => {
    policies.push({
      action: "datasource_connection:get",
      resource: policy.selector,
    });
    if (
      policy.execution_request_read ||
      policy.execution_request_write ||
      policy.execution_request_review
    ) {
      policies.push({
        action: "execution_request:get",
        resource: policy.selector,
      });
    }
    if (policy.execution_request_review) {
      policies.push({
        action: "execution_request:review",
        resource: policy.selector,
      });
    }
    if (policy.execution_request_write) {
      policies.push({
        action: "execution_request:edit",
        resource: policy.selector,
      });
      policies.push({
        action: "execution_request:execute",
        resource: policy.selector,
      });
    }
  });
  policies.push(...role.otherPolicies);

  return {
    id: role.id,
    name: role.name,
    description: role.description,
    policies,
  };
};

export {
  useRole,
  newConnectionPolicy,
  transformRole,
  transformToPayload,
  RoleSchema,
  UserPolicySchema,
  RolePolicy,
  ConnectionPolicy,
};

export type { Role };
