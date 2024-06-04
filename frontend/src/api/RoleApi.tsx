import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

const policyResponseSchema = z.object({
  id: z.string(),
  action: z.string(),
  effect: z.string(),
  resource: z.string(),
});

const policyUpdatePayloadSchema = policyResponseSchema.omit({
  id: true,
});

const roleResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  policies: z.array(policyResponseSchema),
  isDefault: z.boolean(),
});

type PolicyUpdatePayload = z.infer<typeof policyUpdatePayloadSchema>;

const rolesResponseSchema = z.object({
  roles: roleResponseSchema.array(),
});

const roleUpdatePayloadSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  policies: z.array(policyUpdatePayloadSchema),
});

const createRoleRequestSchema = roleUpdatePayloadSchema.omit({
  id: true,
});

type RoleResponse = z.infer<typeof roleResponseSchema>;
type RoleUpdatePayload = z.infer<typeof roleUpdatePayloadSchema>;
type PolicyResponse = z.infer<typeof policyResponseSchema>;
type CreateRoleRequest = z.infer<typeof createRoleRequestSchema>;
type RolesResponse = z.infer<typeof rolesResponseSchema>;

const getRoles = async (): Promise<ApiResponse<RolesResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/roles/`,
    {
      method: "GET",
      credentials: "include",
    },
    rolesResponseSchema,
  );
};

const getRole = async (id: string): Promise<ApiResponse<RoleResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/roles/${id}`,
    {
      method: "GET",
      credentials: "include",
    },
    roleResponseSchema,
  );
};

const createRole = async (
  role: CreateRoleRequest,
): Promise<ApiResponse<RoleResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/roles/`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(role),
    },
    roleResponseSchema,
  );
};

const removeRole = async (id: string): Promise<ApiResponse<void>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/roles/${id}`,
    {
      method: "DELETE",
      credentials: "include",
    },
    z.undefined(),
  );
};

const patchRole = async (
  id: string,
  role: RoleUpdatePayload,
): Promise<ApiResponse<RoleResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/roles/${id}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(role),
    },
    roleResponseSchema,
  );
};

export {
  roleResponseSchema,
  getRoles,
  createRole,
  patchRole,
  removeRole,
  getRole,
};

export type {
  RoleResponse,
  PolicyResponse,
  CreateRoleRequest,
  PolicyUpdatePayload,
  RoleUpdatePayload,
};
