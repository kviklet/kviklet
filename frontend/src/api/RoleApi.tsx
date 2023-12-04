import { z } from "zod";
import baseUrl from "./base";

const policyResponseSchema = z.object({
  id: z.string(),
  action: z.string(),
  effect: z.string(),
  resource: z.string(),
});

const policyPatchSchema = policyResponseSchema.omit({
  id: true,
});

// Define the schema for the user response
const roleResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  policies: z.array(policyResponseSchema),
});

type PolicyPatch = z.infer<typeof policyPatchSchema>;

const createRoleRequestSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
});

const rolesResponseSchema = z.object({
  roles: roleResponseSchema.array(),
});

const rolePatchSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  policies: z.array(policyPatchSchema),
});

type RoleResponse = z.infer<typeof roleResponseSchema>;
type RolePatch = z.infer<typeof rolePatchSchema>;
type PolicyResponse = z.infer<typeof policyResponseSchema>;
type CreateRoleRequest = z.infer<typeof createRoleRequestSchema>;

const getRoles = async (): Promise<RoleResponse[]> => {
  const response = await fetch(`${baseUrl}/roles/`, {
    method: "GET",
    credentials: "include",
  });

  const data: unknown = await response.json();
  return rolesResponseSchema.parse(data).roles;
};

const createRole = async (role: CreateRoleRequest): Promise<RoleResponse> => {
  const response = await fetch(`${baseUrl}/roles/`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(role),
  });
  const data: unknown = await response.json();
  return roleResponseSchema.parse(data);
};

const removeRole = async (id: string): Promise<void> => {
  await fetch(`${baseUrl}/roles/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  return;
};

const patchRole = async (
  id: string,
  role: RolePatch,
): Promise<RoleResponse> => {
  const response = await fetch(`${baseUrl}/roles/${id}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(role),
  });
  const data: unknown = await response.json();
  return roleResponseSchema.parse(data);
};

export { roleResponseSchema, getRoles, createRole, patchRole, removeRole };
export type {
  RoleResponse,
  PolicyResponse,
  CreateRoleRequest,
  PolicyPatch,
  RolePatch,
};
