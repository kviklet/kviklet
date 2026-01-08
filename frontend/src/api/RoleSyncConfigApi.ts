import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

// Zod schemas
const RoleSyncMappingSchema = z.object({
  id: z.string(),
  idpGroupName: z.string(),
  roleId: z.string(),
  roleName: z.string(),
});

const RoleSyncConfigResponseSchema = z.object({
  enabled: z.boolean(),
  syncMode: z.enum(["FULL_SYNC", "ADDITIVE", "FIRST_LOGIN_ONLY"]),
  groupsAttribute: z.string(),
  mappings: z.array(RoleSyncMappingSchema),
});

const RoleSyncConfigUpdateSchema = z.object({
  enabled: z.boolean(),
  syncMode: z.enum(["FULL_SYNC", "ADDITIVE", "FIRST_LOGIN_ONLY"]),
  groupsAttribute: z.string(),
});

const AddRoleSyncMappingRequestSchema = z.object({
  idpGroupName: z.string(),
  roleId: z.string(),
});

// Types
export type RoleSyncMapping = z.infer<typeof RoleSyncMappingSchema>;
export type RoleSyncConfigResponse = z.infer<
  typeof RoleSyncConfigResponseSchema
>;
export type RoleSyncConfigUpdate = z.infer<typeof RoleSyncConfigUpdateSchema>;
export type AddRoleSyncMappingRequest = z.infer<
  typeof AddRoleSyncMappingRequestSchema
>;

// API functions
export async function getRoleSyncConfig(): Promise<
  ApiResponse<RoleSyncConfigResponse>
> {
  return fetchWithErrorHandling(
    `${baseUrl}/config/role-sync/`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    RoleSyncConfigResponseSchema,
  );
}

export async function updateRoleSyncConfig(
  config: RoleSyncConfigUpdate,
): Promise<ApiResponse<RoleSyncConfigResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/config/role-sync/`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(config),
    },
    RoleSyncConfigResponseSchema,
  );
}

export async function addRoleSyncMapping(
  request: AddRoleSyncMappingRequest,
): Promise<ApiResponse<RoleSyncMapping>> {
  return fetchWithErrorHandling(
    `${baseUrl}/config/role-sync/mappings`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    },
    RoleSyncMappingSchema,
  );
}

export async function deleteRoleSyncMapping(
  id: string,
): Promise<ApiResponse<void>> {
  return fetchWithErrorHandling(
    `${baseUrl}/config/role-sync/mappings/${id}`,
    {
      method: "DELETE",
      credentials: "include",
    },
    z.undefined(),
  );
}
