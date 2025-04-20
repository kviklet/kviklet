import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

// Define the Zod schema for ExecutionLogResponse
const ExecutionLogResponseSchema = z.object({
  requestId: z.string(),
  name: z.string(),
  statement: z.string(),
  connectionId: z.string(),
  executionTime: z.coerce.date(),
});

// Define the Zod schema for ExecutionsResponse
const ExecutionsResponseSchema = z.object({
  executions: z.array(ExecutionLogResponseSchema),
});

// Types inferred from the schemas
type ExecutionLogResponse = z.infer<typeof ExecutionLogResponseSchema>;
type ExecutionsResponse = z.infer<typeof ExecutionsResponseSchema>;

const getExecutions = async (): Promise<ApiResponse<ExecutionsResponse>> => {
  const response = await fetchWithErrorHandling(
    `${baseUrl}/executions/`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    ExecutionsResponseSchema,
  );
  return response;
};

export { getExecutions };

export type { ExecutionLogResponse, ExecutionsResponse };
