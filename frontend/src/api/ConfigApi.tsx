import { z } from "zod";
import baseUrl from "./base";

const ConfigResponseSchema = z.object({
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
