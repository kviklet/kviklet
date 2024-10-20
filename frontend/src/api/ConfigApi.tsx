import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

const ConfigResponseSchema = z.object({
  oauthProvider: z.string().nullable().optional(),
  ldapEnabled: z.boolean(),
  teamsUrl: z.string().nullable().optional(),
  slackUrl: z.string().nullable().optional(),
  liveSessionEnabled: z.boolean().optional(),
});

export const ConfigPayloadSchema = ConfigResponseSchema.omit({
  oauthProvider: true,
  ldapEnabled: true,
});

export type ConfigResponse = z.infer<typeof ConfigResponseSchema>;
export type ConfigPayload = z.infer<typeof ConfigPayloadSchema>;

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

export async function putConfig(
  config: ConfigPayload,
): Promise<ApiResponse<ConfigResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/config/`,
    {
      method: "PUT",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(config),
    },
    ConfigResponseSchema,
  );
}
