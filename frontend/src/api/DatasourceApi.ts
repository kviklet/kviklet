import { z } from "zod";
import baseUrl, { withType } from "./base";
import {
  ApiResponse,
  fetchEmptyWithErrorHandling,
  fetchWithErrorHandling,
} from "./Errors";

enum AuthenticationType {
  USER_PASSWORD = "USER_PASSWORD",
  AWS_IAM = "AWS_IAM",
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
    }),
    additionalJDBCOptions: z.string().optional(),
    dumpsEnabled: z.boolean(),
    temporaryAccessEnabled: z.boolean(),
    explainEnabled: z.boolean(),
    roleArn: z.string().nullable(),
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

interface ConnectionBase {
  displayName: string;
  id: string;
  description: string;
  reviewConfig: {
    numTotalRequired: number;
  };
  maxExecutions: number | null;
}

interface DatabaseConnectionBase extends ConnectionBase {
  connectionType: "DATASOURCE";
  databaseName: string;
  type: DatabaseType;
  protocol: DatabaseProtocol;
  hostname: string;
  port: number;
  additionalJDBCOptions?: string;
}

type DatabaseConnection =
  | (DatabaseConnectionBase & {
      authenticationType: "USER_PASSWORD";
      username: string;
      password: string;
    })
  | (DatabaseConnectionBase & {
      authenticationType: "AWS_IAM";
      username: string;
      roleArn: string | null;
    });

interface KubernetesConnection extends ConnectionBase {
  connectionType: "KUBERNETES";
}

type ConnectionPayload = DatabaseConnection | KubernetesConnection;

type AllNullableExcept<T, K extends keyof T> = {
  [P in keyof T]: P extends K ? T[P] : T[P] | undefined;
};

// Use a distributive conditional type to preserve the union structure
type PatchDatabaseConnectionPayload = DatabaseConnection extends infer T
  ? T extends DatabaseConnection
    ? Omit<AllNullableExcept<T, "connectionType" | "authenticationType">, "id">
    : never
  : never;

type PatchKubernetesConnectionPayload = Omit<
  AllNullableExcept<KubernetesConnection, "connectionType">,
  "id"
>;

type PatchConnectionPayload =
  | PatchDatabaseConnectionPayload
  | PatchKubernetesConnectionPayload;

// extract the inferred type
type ConnectionResponse = z.infer<typeof connectionResponseSchema>;
type DatabaseConnectionResponse = z.infer<
  typeof databaseConnectionResponseSchema
>;
type KubernetesConnectionResponse = z.infer<
  typeof kubernetesConnectionResponseSchema
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

const deleteConnection = async (id: string): Promise<ApiResponse<void>> => {
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
  deleteConnection,
};

export type {
  TestConnectionResponse,
  ConnectionResponse,
  ConnectionPayload,
  PatchConnectionPayload,
  DatabaseConnectionResponse,
  KubernetesConnectionResponse,
  PatchDatabaseConnectionPayload,
  PatchKubernetesConnectionPayload,
};
