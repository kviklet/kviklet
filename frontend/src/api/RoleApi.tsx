import { z } from "zod";
import baseUrl from "./base";

const permissionResponseSchema = z.object({
  id: z.string(),
  action: z.string(),
  effect: z.string(),
  resource: z.string(),
});

// Define the schema for the user response
const roleResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  permissions: z.array(permissionResponseSchema),
});

const editRoleRequestSchema = roleResponseSchema.omit({
  id: true,
});

type EditRoleRequest = z.infer<typeof editRoleRequestSchema>;

const createRoleRequestSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
});

const rolesResponseSchema = z.object({
  roles: roleResponseSchema.array(),
});

type RoleResponse = z.infer<typeof roleResponseSchema>;
type PermissionResponse = z.infer<typeof permissionResponseSchema>;
type CreateRoleRequest = z.infer<typeof createRoleRequestSchema>;

const getRoles = async (): Promise<RoleResponse[]> => {
  const response = await fetch(`${baseUrl}/roles/`, {
    method: "GET",
    credentials: "include",
  });

  const data = await response.json();
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
  const data = await response.json();
  return roleResponseSchema.parse(data);
};

const removeRole = async (id: string): Promise<void> => {
  const response = await fetch(`${baseUrl}/roles/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  return;
};

const patchRole = async (
  id: string,
  role: EditRoleRequest,
): Promise<RoleResponse> => {
  const response = await fetch(`${baseUrl}/roles/${id}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(role),
  });
  const data = await response.json();
  return roleResponseSchema.parse(data);
};

export { roleResponseSchema, getRoles, createRole, patchRole, removeRole };
export type { RoleResponse, PermissionResponse, CreateRoleRequest };
