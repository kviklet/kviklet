import { z } from "zod";
import { roleResponseSchema } from "./RoleApi";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

const userResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  fullName: z.string().nullable(),
  roles: roleResponseSchema.array(),
});

type UserResponse = z.infer<typeof userResponseSchema>;

const createUserRequestSchema = z.object({
  email: z.string().min(3).max(50),
  password: z.string().min(6).max(50),
  fullName: z.string().min(1).max(50),
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

export { fetchUsers, userResponseSchema, createUser, updateUser };
export type { UserResponse };
