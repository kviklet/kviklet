import { z } from "zod";

const datasourceUrl = "http://localhost:8080/datasources/";

enum AuthenticationType {
  USER_PASSWORD = "USER_PASSWORD",
}

const ConnectionResponse = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  authenticationType: z.nativeEnum(AuthenticationType),
});

const DatabaseResponse = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  datasourceType: z.enum(["POSTGRESQL", "MYSQL"]).or(z.string()),
  hostname: z.coerce.string(),
  port: z.coerce.number(),
  datasourceConnections: z.array(ConnectionResponse),
});

const ApiResponse = z.object({
  databases: z.array(DatabaseResponse),
});

const DatabasePayload = DatabaseResponse.omit({
  id: true,
  datasourceConnections: true,
});

const ConnectionPayload = z.object({
  displayName: z.coerce.string(),
  username: z.string(),
  password: z.string(),
});

const fetchDatabases = async (): Promise<Database[]> => {
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
type Database = z.infer<typeof DatabaseResponse>;
type Connection = z.infer<typeof ConnectionResponse>;

const addDatabase = async (
  payload: z.infer<typeof DatabasePayload>
): Promise<boolean> => {
  const response = await fetch(datasourceUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  return true;
};

const addConnection = async (
  payload: z.infer<typeof ConnectionPayload>,
  datasourceId: string
): Promise<boolean> => {
  const response = await fetch(`${datasourceUrl}${datasourceId}/connections`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  return true;
};

export {
  fetchDatabases,
  addDatabase,
  addConnection,
  DatabaseResponse,
  ConnectionResponse,
  DatabasePayload,
  ConnectionPayload,
  AuthenticationType,
};

export type { Database, Connection };
