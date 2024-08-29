import { z } from "zod";
import baseUrl, { withType } from "./base";
import {
  ApiResponse,
  fetchEmptyWithErrorHandling,
  fetchWithErrorHandling,
} from "./Errors";

enum AuthenticationType {
  USER_PASSWORD = "USER_PASSWORD",
}

enum DatabaseType {
  POSTGRES = "POSTGRESQL",
  MYSQL = "MYSQL",
  MSSQL = "MSSQL",
  MONGODB = "MONGODB",
  MARIADB = "MARIADB",
}

enum DatabaseProtocol {
  POSTGRESQL = "POSTGRESQL",
  MYSQL = "MYSQL",
  MSSQL = "MSSQL",
  MONGODB = "MONGODB",
  MONGODB_SRV = "MONGODB_SRV",
  MARIADB = "MARIADB",
}

const databaseConnectionResponseSchema = withType(
  z.object({
    id: z.coerce.string(),
    displayName: z.coerce.string(),
    type: z.nativeEnum(DatabaseType),
    protocol: z.nativeEnum(DatabaseProtocol),
    maxExecutions: z.coerce.number().nullable(),
    authenticationType: z.nativeEnum(AuthenticationType),
    username: z.coerce.string(),
    hostname: z.coerce.string(),
    port: z.coerce.number(),
    description: z.coerce.string(),
    databaseName: z.coerce.string().nullable(),
    reviewConfig: z.object({
      numTotalRequired: z.number(),
      fourEyesRequired: z.boolean()
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
      numTotalRequired: z.coerce.number()
    }),
    maxExecutions: z.coerce.number().nullable(),
  }),
  "KUBERNETES",
);

const connectionResponseSchema = z.union([
  databaseConnectionResponseSchema,
  kubernetesConnectionResponseSchema,
]);

const testConnectionResponseSchema = z.object({
  success: z.boolean(),
  details: z.string(),
  accessibleDatabases: z.array(z.string()),
});

const databaseConnectionPayloadSchema = z
  .object({
    displayName: z.coerce.string(),
    id: z.string(),
    username: z.string(),
    password: z.string(),
    description: z.string(),
    databaseName: z.string(),
    maxExecutions: z.coerce.number().nullable(),
    reviewConfig: z.object({
      numTotalRequired: z.number(),
      fourEyesRequired: z.boolean(),
    }),
    type: z.nativeEnum(DatabaseType),
    protocol: z.nativeEnum(DatabaseProtocol),
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
    maxExecutions: z.coerce.number().nullable(),
  })
  .transform((data) => ({ ...data, connectionType: "KUBERNETES" }));

const connectionPayloadSchema = z.union([
  databaseConnectionPayloadSchema,
  kubernetesConnectionPayloadSchema,
]);

const patchDatabaseConnectionPayloadSchema = z
  .object({
    displayName: z.coerce.string().optional(),
    protocol: z.nativeEnum(DatabaseProtocol).optional(),
    username: z.string().optional(),
    password: z.string().optional(),
    description: z.string().optional(),
    databaseName: z.string().optional(),
    reviewConfig: z
      .object({
        numTotalRequired: z.number(),
      })
      .optional(),
    maxExecutions: z.number().optional().nullable(),
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
    maxExecutions: z.number().optional().nullable(),
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

type TestConnectionResponse = z.infer<typeof testConnectionResponseSchema>;

const addConnection = async (
  payload: ConnectionPayload,
): Promise<ApiResponse<ConnectionResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/connections/`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    connectionResponseSchema,
  );
};

const testConnection = async (
  payload: ConnectionPayload,
): Promise<ApiResponse<TestConnectionResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/connections/test`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    testConnectionResponseSchema,
  );
};

const patchConnection = async (
  payload: PatchConnectionPayload,
  connectionId: string,
): Promise<ApiResponse<ConnectionResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/connections/${connectionId}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    connectionResponseSchema,
  );
};

const getConnections = async (): Promise<ApiResponse<ConnectionResponse[]>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/connections/`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    z.array(connectionResponseSchema),
  );
};

const getConnection = async (
  id: string,
): Promise<ApiResponse<ConnectionResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/connections/${id}`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    connectionResponseSchema,
  );
};

const deleteConnection = async (id: string): Promise<ApiResponse<null>> => {
  return fetchEmptyWithErrorHandling(`${baseUrl}/connections/${id}`, {
    method: "DELETE",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
  });
};

export {
  addConnection,
  testConnection,
  connectionResponseSchema,
  databaseConnectionResponseSchema,
  AuthenticationType,
  patchConnection,
  getConnections,
  getConnection,
  DatabaseType,
  DatabaseProtocol,
  kubernetesConnectionPayloadSchema,
  deleteConnection,
};

export type {
  TestConnectionResponse,
  ConnectionResponse,
  ConnectionPayload,
  PatchConnectionPayload,
  KubernetesConnectionPayload,
  DatabaseConnectionResponse,
  KubernetesConnectionResponse,
  PatchDatabaseConnectionPayload,
  PatchKubernetesConnectionPayload,
};
