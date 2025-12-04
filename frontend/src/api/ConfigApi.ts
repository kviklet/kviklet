import { z } from "zod";
import baseUrl from "./base";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";

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
});

export const ConfigPayloadSchema = ConfigResponseSchema.omit({
  oauthProvider: true,
  ldapEnabled: true,
  samlEnabled: true,
  licenseValid: true,
  validUntil: true,
  createdAt: true,
  allowedUsers: true,
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

export async function uploadLicense(file: File): Promise<boolean> {
  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await fetch(`${baseUrl}/config/license/`, {
      method: "POST",
      body: formData,
      credentials: "include",
    });

    if (!response.ok) {
      throw new Error(`Error: ${response.status}`);
    }
    return true;
  } catch (error) {
    console.error("Upload failed:", error);
    return false;
  }
}
