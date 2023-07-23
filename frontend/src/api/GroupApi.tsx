import { z } from "zod";
import baseUrl from "./base";

const permissionResponseSchema = z.object({
  scope: z.string(),
  permissions: z.array(z.string()),
});

// Define the schema for the user response
const groupResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  description: z.string().nullable(),
  permissions: z.array(permissionResponseSchema),
});

const editGroupRequestSchema = groupResponseSchema.omit({
  id: true,
});

type EditGroupRequest = z.infer<typeof editGroupRequestSchema>;

const createGroupRequestSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
});

const groupsResponseSchema = z.object({
  groups: groupResponseSchema.array(),
});

type GroupResponse = z.infer<typeof groupResponseSchema>;
type PermissionResponse = z.infer<typeof permissionResponseSchema>;
type CreateGroupRequest = z.infer<typeof createGroupRequestSchema>;

const getGroups = async (): Promise<GroupResponse[]> => {
  const response = await fetch(`${baseUrl}/groups/`, {
    method: "GET",
    credentials: "include",
  });

  const data = await response.json();
  return groupsResponseSchema.parse(data).groups;
};

const createGroup = async (
  group: CreateGroupRequest
): Promise<GroupResponse> => {
  const response = await fetch(`${baseUrl}/groups/`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(group),
  });
  const data = await response.json();
  return groupResponseSchema.parse(data);
};

const patchGroup = async (
  id: string,
  group: EditGroupRequest
): Promise<GroupResponse> => {
  const response = await fetch(`${baseUrl}/groups/${id}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(group),
  });
  const data = await response.json();
  return groupResponseSchema.parse(data);
};

export { groupResponseSchema, getGroups, createGroup, patchGroup };
export type { GroupResponse, PermissionResponse, CreateGroupRequest };
