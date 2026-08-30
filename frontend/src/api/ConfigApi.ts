import { z } from "zod";
import baseUrl, { apiFetch } from "./base";
import {
  ApiErrorResponse,
  ApiResponse,
  fetchWithErrorHandling,
  isApiErrorResponse,
} from "./Errors";

const ConfigResponseSchema = z.object({
  oauthProvider: z.string().nullable().optional(),
  ldapEnabled: z.boolean(),
  samlEnabled: z.boolean(),
  licenseValid: z.boolean(),
  validUntil: z.coerce.date().nullable().optional(),
  createdAt: z.coerce.date().nullable().optional(),
  allowedUsers: z.number().nullable().optional(),
  teamsUrl: z.string().nullable().optional(),
  slackUrl: z.string().nullable().optional(),
  proxyEnabled: z.boolean().default(false),
  version: z.string(),
  buildDate: z.string(),
  gitCommit: z.string(),
});

export const ConfigPayloadSchema = ConfigResponseSchema.omit({
  oauthProvider: true,
  ldapEnabled: true,
  samlEnabled: true,
  licenseValid: true,
  validUntil: true,
  createdAt: true,
  allowedUsers: true,
  version: true,
  buildDate: true,
  gitCommit: true,
}).extend({
  // Optional on writes: a PUT that leaves it out keeps the stored value, so the
  // notification form can never flip the proxy toggle as a side effect.
  proxyEnabled: z.boolean().optional(),
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

// Returns null on success, the error otherwise — a failed upload (e.g. missing
// configuration:edit) must never look like a success to the caller.
export async function uploadLicense(
  file: File,
): Promise<ApiErrorResponse | null> {
  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await apiFetch(`${baseUrl}/config/license/`, {
      method: "POST",
      body: formData,
      credentials: "include",
    });

    if (response.ok) {
      return null;
    }
    const json: unknown = await response.json().catch(() => null);
    if (isApiErrorResponse(json)) {
      return json;
    }
    return { message: `Error: ${response.status}` };
  } catch (error) {
    return {
      message:
        error instanceof Error ? error.message : "An unknown error occurred",
    };
  }
}
