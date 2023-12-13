import { z } from "zod";
import baseUrl from "./base";

enum AuthenticationType {
  USER_PASSWORD = "USER_PASSWORD",
}

enum DatabaseType {
  POSTGRES = "POSTGRES",
  MYSQL = "MYSQL",
}

const connectionResponseSchema = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  authenticationType: z.nativeEnum(AuthenticationType),
  shortUsername: z.coerce.string(),
  description: z.coerce.string(),
  databaseName: z.string().nullable(),
  reviewConfig: z.object({
    numTotalRequired: z.number(),
  }),
});

const connectionPayloadSchema = z.object({
  displayName: z.coerce.string(),
  id: z.string(),
  username: z.string(),
  password: z.string(),
  description: z.string(),
  databaseName: z.string(),
  reviewConfig: z.object({
    numTotalRequired: z.number(),
  }),
  type: z.nativeEnum(DatabaseType),
  hostname: z.string(),
  port: z.number(),
});

const patchConnectionPayloadSchema = z.object({
  displayName: z.coerce.string().optional(),
  username: z.string().optional(),
  password: z.string().optional(),
  description: z.string().optional(),
  databaseName: z.string().optional(),
  reviewConfig: z
    .object({
      numTotalRequired: z.number(),
    })
    .optional(),
});

// extract the inferred type
type ConnectionResponse = z.infer<typeof connectionResponseSchema>;
type ConnectionPayload = z.infer<typeof connectionPayloadSchema>;
type PatchConnectionPayload = z.infer<typeof patchConnectionPayloadSchema>;

const addConnection = async (
  payload: ConnectionPayload,
): Promise<ConnectionResponse> => {
  const response = await fetch(`${baseUrl}/connections/`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const connection = connectionResponseSchema.parse(await response.json());
  return connection;
};

const patchConnection = async (
  payload: PatchConnectionPayload,
  connectionId: string,
): Promise<ConnectionResponse> => {
  const response = await fetch(`${baseUrl}/connections/${connectionId}`, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const connection = connectionResponseSchema.parse(await response.json());
  return connection;
};

const getConnections = async (): Promise<ConnectionResponse[]> => {
  const response = await fetch(`${baseUrl}/connections/`, {
    method: "GET",
    credentials: "include",
  });
  const data: unknown = await response.json();
  return z.array(connectionResponseSchema).parse(data);
};

export {
  addConnection,
  connectionResponseSchema,
  AuthenticationType,
  patchConnection,
  getConnections,
  DatabaseType,
};

export type { ConnectionResponse, ConnectionPayload, PatchConnectionPayload };
