import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

const PodSchema = z.object({
  id: z.string(),
  name: z.string(),
  namespace: z.string(),
  status: z.enum(["Running", "Pending", "Succeeded", "Failed", "Unknown"]),
  containerNames: z.array(z.string()),
});

const PodsResponseSchema = z.object({
  pods: z.array(PodSchema),
});

const CommandRequestSchema = z.object({
  namespace: z.string(),
  podName: z.string(),
  command: z.string(),
});

const CommandResposneSchema = z.object({
  output: z.string(),
  error: z.string(),
});

type CommandRequest = z.infer<typeof CommandRequestSchema>;
type CommandResponse = z.infer<typeof CommandResposneSchema>;

type Pod = z.infer<typeof PodSchema>;
type PodsResponse = z.infer<typeof PodsResponseSchema>;

export type { Pod, PodsResponse };

const getPods = async (): Promise<ApiResponse<PodsResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/kubernetes/pods`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    PodsResponseSchema,
  );
};

const executeCommand = async (
  command: CommandRequest,
): Promise<ApiResponse<CommandResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/kubernetes/execute-command`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(command),
    },
    CommandResposneSchema,
  );
};

export { getPods, executeCommand };
