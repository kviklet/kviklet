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
  return await fetchWithErrorHandling(
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
};

const exportExecutions = async (): Promise<void> => {
  const response = await fetch(`${baseUrl}/executions/export`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(`Export failed: ${response.statusText}`);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const contentDisposition = response.headers.get("Content-Disposition");
  const filenameMatch = contentDisposition?.match(/filename="(.+)"/);
  const filename = filenameMatch ? filenameMatch[1] : "auditlog-export.txt";

  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
};

export { getExecutions, exportExecutions };

export type { ExecutionLogResponse, ExecutionsResponse };
