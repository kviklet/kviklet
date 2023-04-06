import { z } from "zod";

const datasourceUrl = "http://localhost:8080/datasources/";

const Connection = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  authenticationType: z.enum(["USER_PASSWORD"]),
  username: z.string(),
  password: z.string(),
});

const Database = z.object({
  id: z.coerce.string(),
  displayName: z.coerce.string(),
  datasourceType: z.enum(["POSTGRESQL"]),
  hostname: z.coerce.string(),
  port: z.coerce.number(),
  datasourceConnections: z.array(Connection),
});

const ApiResponse = z.object({
  databases: z.array(Database),
});

const DatabasePayload = Database.omit({
  id: true,
  datasourceConnections: true,
});
const ConnectionPayload = Connection.omit({
  id: true,
  authenticationType: true,
});

const fetchDatabases = async (): Promise<Database[]> => {
  const response = await fetch(datasourceUrl);
  const json = await response.json();
  console.log(json);
  const parsedResponse = ApiResponse.parse(json);
  return parsedResponse.databases;
};

// extract the inferred type
type Database = z.infer<typeof Database>;
type Connection = z.infer<typeof Connection>;

const addDatabase = async (
  payload: z.infer<typeof DatabasePayload>
): Promise<boolean> => {
  const response = await fetch(datasourceUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
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
    body: JSON.stringify(payload),
  });
  return true;
};

export {
  fetchDatabases,
  addDatabase,
  addConnection,
  Database,
  Connection,
  DatabasePayload,
  ConnectionPayload,
};
