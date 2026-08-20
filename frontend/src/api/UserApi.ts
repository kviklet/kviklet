import { z } from "zod";
import { roleResponseSchema } from "./RoleApi";
import baseUrl from "./base";
import {
  ApiResponse,
  fetchEmptyWithErrorHandling,
  fetchWithErrorHandling,
} from "./Errors";

const oncallGrantKindSchema = z.enum(["ONCALL", "OUTAGE"]);

const oncallGrantResponseSchema = z.object({
  id: z.string(),
  userId: z.string(),
  kind: oncallGrantKindSchema,
  reason: z.string().nullable().optional(),
  startsAt: z.string(),
  endsAt: z.string(),
  bypassApproval: z.boolean(),
  grantedByUserId: z.string(),
  createdAt: z.string(),
  status: z.enum(["PENDING", "ACTIVE", "ENDED"]).optional(),
  durationMinutes: z.number().optional(),
  approvedAt: z.string().nullable().optional(),
});

const userResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  fullName: z.string().nullable(),
  roles: roleResponseSchema.array(),
  activeOncallGrant: oncallGrantResponseSchema.nullable().optional(),
  pendingOncallGrant: oncallGrantResponseSchema.nullable().optional(),
});

type UserResponse = z.infer<typeof userResponseSchema>;
type OncallGrantResponse = z.infer<typeof oncallGrantResponseSchema>;
type OncallGrantKind = z.infer<typeof oncallGrantKindSchema>;

const createUserRequestSchema = z.object({
  email: z
    .string()
    .min(3, "Email must be between 3 and 50 characters.")
    .max(50, "Email must be between 3 and 50 characters."),
  password: z
    .string()
    .min(6, "Password must be between 6 and 50 characters.")
    .max(50, "Password must be between 6 and 50 characters."),
  fullName: z
    .string()
    .min(1, "Full name is required.")
    .max(50, "Full name must be at most 50 characters."),
});

const usersResponseSchema = z.object({
  users: userResponseSchema.array(),
});

const UpdateUserRequestSchema = z.object({
  email: z.string().min(3).max(50).optional(),
  fullName: z.string().min(1).max(50).optional(),
  roles: z.array(z.string()).optional(),
  password: z.string().optional(),
});

type UpdateUserRequest = z.infer<typeof UpdateUserRequestSchema>;
type UsersResponse = z.infer<typeof usersResponseSchema>;

type CreateUserRequest = z.infer<typeof createUserRequestSchema>;

type StartOncallGrantRequest = {
  kind: OncallGrantKind;
  durationMinutes: number;
  reason?: string;
  bypassApproval?: boolean;
};

async function fetchUsers(): Promise<ApiResponse<UsersResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/users/`,
    {
      method: "GET",
      credentials: "include",
    },
    usersResponseSchema,
  );
}

async function createUser(
  request: CreateUserRequest,
): Promise<ApiResponse<UserResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/users/`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    },
    userResponseSchema,
  );
}

async function updateUser(
  id: string,
  request: UpdateUserRequest,
): Promise<ApiResponse<UserResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/users/${id}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    },
    userResponseSchema,
  );
}

async function startOncallGrant(
  userId: string,
  request: StartOncallGrantRequest,
): Promise<ApiResponse<OncallGrantResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/users/${userId}/oncall-grant`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    },
    oncallGrantResponseSchema,
  );
}

async function requestOncallGrant(
  userId: string,
  request: StartOncallGrantRequest,
): Promise<ApiResponse<OncallGrantResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/users/${userId}/oncall-grant/request`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(request),
    },
    oncallGrantResponseSchema,
  );
}

async function approveOncallGrant(
  userId: string,
): Promise<ApiResponse<OncallGrantResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/users/${userId}/oncall-grant/approve`,
    {
      method: "POST",
      credentials: "include",
    },
    oncallGrantResponseSchema,
  );
}

async function revokeOncallGrant(userId: string) {
  return fetchEmptyWithErrorHandling(
    `${baseUrl}/users/${userId}/oncall-grant`,
    {
      method: "DELETE",
      credentials: "include",
    },
  );
}

export {
  fetchUsers,
  userResponseSchema,
  oncallGrantResponseSchema,
  createUser,
  createUserRequestSchema,
  updateUser,
  startOncallGrant,
  requestOncallGrant,
  approveOncallGrant,
  revokeOncallGrant,
};
export type {
  UserResponse,
  CreateUserRequest,
  OncallGrantResponse,
  OncallGrantKind,
  StartOncallGrantRequest,
};
