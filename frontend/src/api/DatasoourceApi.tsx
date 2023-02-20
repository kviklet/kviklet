import { z } from "zod";

const datasourceUrl = "http://localhost:8080/datasource/";

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
  port: z.number(),
  datasourceConnections: z.array(Connection),
});

const ApiResponse = z.object({
  databases: z.array(Database),
});

// extract the inferred type
type Database = z.infer<typeof Database>;
type Connection = z.infer<typeof Connection>;

const fetchDatabases = async (): Promise<Database[]> => {
  const response = await fetch(datasourceUrl);
  const json = await response.json();
  console.log(json);
  const parsedResponse = ApiResponse.parse(json);
  return parsedResponse.databases;
};

export { fetchDatabases, Database, Connection };
