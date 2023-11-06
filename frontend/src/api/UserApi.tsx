import { z } from "zod";
import { roleResponseSchema } from "./RoleApi";
import baseUrl from "./base";

// Define the schema for the user response
const userResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  fullName: z.string().nullable(),
  roles: roleResponseSchema.array(),
});

// Define the type for the user response
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

type CreateUserRequest = z.infer<typeof createUserRequestSchema>;

async function fetchUsers(): Promise<UserResponse[]> {
  const response = await fetch(`${baseUrl}/users/`, {
    method: "GET",
    credentials: "include",
  });

  const data: unknown = await response.json();
  return usersResponseSchema.parse(data).users;
}

async function createUser(request: CreateUserRequest): Promise<UserResponse> {
  const response = await fetch(`${baseUrl}/users/`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(request),
  });
  const data: unknown = await response.json();
  return userResponseSchema.parse(data);
}

async function updateUser(
  id: string,
  request: UpdateUserRequest,
): Promise<UserResponse> {
  const response = await fetch(`${baseUrl}/users/${id}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(request),
  });
  const data: unknown = await response.json();
  return userResponseSchema.parse(data);
}

export { fetchUsers, userResponseSchema, createUser, updateUser };
export type { UserResponse };
