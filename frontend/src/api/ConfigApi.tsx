import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

const ConfigResponseSchema = z.object({
  oauthProvider: z.string().nullable().optional(),
});

export type ConfigResponse = z.infer<typeof ConfigResponseSchema>;

export async function getConfig(): Promise<ApiResponse<ConfigResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/config/`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    ConfigResponseSchema,
  );
}
