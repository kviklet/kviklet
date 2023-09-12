import { z } from "zod";

const baseUrl = `${window.location.protocol}//${window.location.hostname}:8080`;

const datasourceUrl = `${baseUrl}/datasources/`;

enum AuthenticationType {
  USER_PASSWORD = "USER_PASSWORD",
}

const connectionResponseSchema = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  authenticationType: z.nativeEnum(AuthenticationType),
  shortUsername: z.coerce.string(),
  description: z.coerce.string(),
  reviewConfig: z.object({
    numTotalRequired: z.number(),
  }),
});

const databaseResponseSchema = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  datasourceType: z.enum(["POSTGRESQL", "MYSQL"]).or(z.string()),
  hostname: z.coerce.string(),
  port: z.coerce.number(),
  datasourceConnections: z.array(connectionResponseSchema),
});

const ApiResponse = z.object({
  databases: z.array(databaseResponseSchema),
});

const databasePayloadSchema = databaseResponseSchema.omit({
  id: true,
  datasourceConnections: true,
});

const connectionPayloadSchema = z.object({
  displayName: z.coerce.string(),
  username: z.string(),
  password: z.string(),
  reviewConfig: z.object({
    numTotalRequired: z.number(),
  }),
});

const patchConnectionPayloadSchema = z.object({
  displayName: z.coerce.string().optional(),
  username: z.string().optional(),
  password: z.string().optional(),
  reviewConfig: z
    .object({
      numTotalRequired: z.number(),
    })
    .optional(),
});

const fetchDatabases = async (): Promise<DatabaseResponse[]> => {
  const response = await fetch(datasourceUrl, {
    method: "GET",
    credentials: "include",
  });
  const json = await response.json();
  console.log(json);
  const parsedResponse = ApiResponse.parse(json);
  return parsedResponse.databases;
};

// extract the inferred type
type DatabaseResponse = z.infer<typeof databaseResponseSchema>;
type ConnectionResponse = z.infer<typeof connectionResponseSchema>;
type DatabasePayload = z.infer<typeof databasePayloadSchema>;
type ConnectionPayload = z.infer<typeof connectionPayloadSchema>;
type PatchConnectionPayload = z.infer<typeof patchConnectionPayloadSchema>;

const addDatabase = async (
  payload: DatabasePayload,
): Promise<DatabaseResponse> => {
  const response = await fetch(datasourceUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const newDatabase = databaseResponseSchema.parse(await response.json());
  return newDatabase;
};

const addConnection = async (
  payload: ConnectionPayload,
  datasourceId: string,
): Promise<ConnectionResponse> => {
  const response = await fetch(`${datasourceUrl}${datasourceId}/connections`, {
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
  datasourceId: string,
  connectionId: string,
): Promise<ConnectionResponse> => {
  const response = await fetch(
    `${datasourceUrl}${datasourceId}/connections/${connectionId}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
  );
  const connection = connectionResponseSchema.parse(await response.json());
  return connection;
};

const removeDatabase = async (id: string): Promise<void> => {
  const response = await fetch(`${datasourceUrl}${id}`, {
    method: "DELETE",
    credentials: "include",
  });
  return;
};

export {
  fetchDatabases,
  addDatabase,
  addConnection,
  removeDatabase,
  databaseResponseSchema,
  connectionResponseSchema,
  AuthenticationType,
  patchConnection,
};

export type {
  DatabaseResponse,
  ConnectionResponse,
  DatabasePayload,
  ConnectionPayload,
  PatchConnectionPayload,
};
