import { z } from "zod";
import baseUrl from "./base";

// Define the Zod schema for ExecutionLogResponse
const ExecutionLogResponseSchema = z.object({
  requestId: z.string(),
  name: z.string(),
  statement: z.string(), // Ensure the field names are in camelCase for JavaScript
  connectionId: z.string(),
  executionTime: z.preprocess((arg) => {
    if (typeof arg === "string") {
      return new Date(arg);
    }
  }, z.date()),
});

// Define the Zod schema for ExecutionsResponse
const ExecutionsResponseSchema = z.object({
  executions: z.array(ExecutionLogResponseSchema),
});

// Types inferred from the schemas
type ExecutionLogResponse = z.infer<typeof ExecutionLogResponseSchema>;
type ExecutionsResponse = z.infer<typeof ExecutionsResponseSchema>;

const getExecutions = async (): Promise<ExecutionsResponse> => {
  const response = await fetch(`${baseUrl}/executions/`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
  });
  return ExecutionsResponseSchema.parse(await response.json());
};

export { getExecutions };

export type { ExecutionLogResponse, ExecutionsResponse };
