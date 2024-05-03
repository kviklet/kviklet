import { z } from "zod";
import baseUrl, { withType } from "./base";

enum AuthenticationType {
  USER_PASSWORD = "USER_PASSWORD",
}

enum DatabaseType {
  POSTGRES = "POSTGRESQL",
  MYSQL = "MYSQL",
  MSSQL = "MSSQL",
}

const databaseConnectionResponseSchema = withType(
  z.object({
    id: z.coerce.string(),
    displayName: z.coerce.string(),
    type: z.nativeEnum(DatabaseType),
    authenticationType: z.nativeEnum(AuthenticationType),
    username: z.coerce.string(),
    hostname: z.coerce.string(),
    port: z.coerce.number(),
    description: z.coerce.string(),
    databaseName: z.coerce.string().nullable(),
    reviewConfig: z.object({
      numTotalRequired: z.number(),
    }),
    additionalJDBCOptions: z.string().optional(),
  }),
  "DATASOURCE",
);

const kubernetesConnectionResponseSchema = withType(
  z.object({
    id: z.coerce.string(),
    displayName: z.coerce.string(),
    description: z.coerce.string(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
    }),
  }),
  "KUBERNETES",
);

const connectionResponseSchema = z.union([
  databaseConnectionResponseSchema,
  kubernetesConnectionResponseSchema,
]);

const databaseConnectionPayloadSchema = z
  .object({
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
    additionalJDBCOptions: z.string().optional(),
  })
  .transform((data) => ({ ...data, connectionType: "DATASOURCE" }));

const kubernetesConnectionPayloadSchema = z
  .object({
    displayName: z.coerce.string(),
    id: z.string(),
    description: z.string(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
    }),
  })
  .transform((data) => ({ ...data, connectionType: "KUBERNETES" }));

const connectionPayloadSchema = z.union([
  databaseConnectionPayloadSchema,
  kubernetesConnectionPayloadSchema,
]);

const patchDatabaseConnectionPayloadSchema = z
  .object({
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
  })
  .transform((data) => ({ ...data, connectionType: "DATASOURCE" }));

const patchKubernetesConnectionPayloadSchema = z
  .object({
    displayName: z.coerce.string().optional(),
    description: z.string().optional(),
    reviewConfig: z
      .object({
        numTotalRequired: z.number(),
      })
      .optional(),
  })
  .transform((data) => ({ ...data, connectionType: "KUBERNETES" }));

const patchConnectionPayloadSchema = z.union([
  patchDatabaseConnectionPayloadSchema,
  patchKubernetesConnectionPayloadSchema,
]);

// extract the inferred type
type ConnectionResponse = z.infer<typeof connectionResponseSchema>;
type DatabaseConnectionResponse = z.infer<
  typeof databaseConnectionResponseSchema
>;
type KubernetesConnectionResponse = z.infer<
  typeof kubernetesConnectionResponseSchema
>;
type ConnectionPayload = z.infer<typeof connectionPayloadSchema>;
type PatchDatabaseConnectionPayload = z.infer<
  typeof patchDatabaseConnectionPayloadSchema
>;
type PatchKubernetesConnectionPayload = z.infer<
  typeof patchKubernetesConnectionPayloadSchema
>;
type PatchConnectionPayload = z.infer<typeof patchConnectionPayloadSchema>;
type KubernetesConnectionPayload = z.infer<
  typeof kubernetesConnectionPayloadSchema
>;

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

const getConnection = async (id: string): Promise<ConnectionResponse> => {
  const response = await fetch(`${baseUrl}/connections/${id}`, {
    method: "GET",
    credentials: "include",
  });
  const data: unknown = await response.json();
  return connectionResponseSchema.parse(data);
};

export {
  addConnection,
  connectionResponseSchema,
  AuthenticationType,
  patchConnection,
  getConnections,
  getConnection,
  DatabaseType,
  kubernetesConnectionPayloadSchema,
};

export type {
  ConnectionResponse,
  ConnectionPayload,
  PatchConnectionPayload,
  KubernetesConnectionPayload,
  DatabaseConnectionResponse,
  KubernetesConnectionResponse,
  PatchDatabaseConnectionPayload,
  PatchKubernetesConnectionPayload,
};
