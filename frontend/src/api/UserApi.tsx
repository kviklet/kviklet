import { z } from "zod";

// Define the schema for the user response
const userResponseSchema = z.object({
  id: z.string(),
  email: z.string(),
  fullName: z.string().nullable(),
});

// Define the type for the user response
type UserResponse = z.infer<typeof userResponseSchema>;

const createUserRequestSchema = z.object({
  email: z.string().min(3).max(50),
  password: z.string().min(6).max(50),
  fullName: z.string().min(1).max(50),
});

type CreateUserRequest = z.infer<typeof createUserRequestSchema>;

async function fetchUsers(): Promise<UserResponse[]> {
  const response = await fetch("/users");
  const data = await response.json();
  return userResponseSchema.array().parse(data);
}

async function createUser(request: CreateUserRequest): Promise<UserResponse> {
  const response = await fetch("/users", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(request),
  });
  const data = await response.json();
  return userResponseSchema.parse(data);
}

export { fetchUsers, userResponseSchema };
export type { UserResponse };
