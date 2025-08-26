import { z } from "zod";
import {
  ApiResponse,
  fetchEmptyWithErrorHandling,
  fetchWithErrorHandling,
} from "./Errors";
import baseUrl from "./base";

const ApiKeyResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  createdAt: z.coerce.date(),
  expiresAt: z.coerce.date().nullable(),
  lastUsedAt: z.coerce.date(),
});

const ApiKeyWithSecretResponseSchema = z.object({
  id: z.string(),
  name: z.string(),
  key: z.string(),
  createdAt: z.coerce.date(),
  expiresAt: z.coerce.date().nullable(),
});

const CreateApiKeyRequestSchema = z.object({
  name: z.string(),
  expiresInDays: z.number().nullable(),
});

export type ApiKeyResponse = z.infer<typeof ApiKeyResponseSchema>;
export type ApiKeyWithSecretResponse = z.infer<
  typeof ApiKeyWithSecretResponseSchema
>;
export type CreateApiKeyRequest = z.infer<typeof CreateApiKeyRequestSchema>;

const ApiKeysResponseSchema = z.object({
  apiKeys: z.array(ApiKeyResponseSchema),
});

export type ApiKeysResponse = z.infer<typeof ApiKeysResponseSchema>;

export async function listApiKeys(): Promise<ApiResponse<ApiKeysResponse>> {
  return await fetchWithErrorHandling(
    `${baseUrl}/api-keys/`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    ApiKeysResponseSchema,
  );
}

export async function createApiKey(
  request: CreateApiKeyRequest,
): Promise<ApiResponse<ApiKeyWithSecretResponse>> {
  return fetchWithErrorHandling(
    `${baseUrl}/api-keys/`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
      credentials: "include",
    },
    ApiKeyWithSecretResponseSchema,
  );
}

export async function deleteApiKey(id: string): Promise<ApiResponse<null>> {
  return fetchEmptyWithErrorHandling(`${baseUrl}/api-keys/${id}`, {
    method: "DELETE",
    credentials: "include",
  });
}
