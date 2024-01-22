import { z } from "zod";
import baseUrl from "./base";
import { DateTime } from "./ExecutionRequestApi";

const ConfigResponseSchema = z.object({
  licenseValid: z.boolean(),
  validUntil: z.coerce.date().nullable().optional(),
  createdAt: DateTime.nullable().optional(),
  allowedUsers: z.number().int().positive().nullable().optional(),
  oauthProvider: z.string().nullable().optional(),
});

export type ConfigResponse = z.infer<typeof ConfigResponseSchema>;

export async function getConfig(): Promise<ConfigResponse> {
  const response = await fetch(`${baseUrl}/config/`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
  });

  return ConfigResponseSchema.parse(await response.json());
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
