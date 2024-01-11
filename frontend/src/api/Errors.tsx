import { z } from "zod";

export const ApiErrorResponseSchema = z.object({
  message: z.string(),
});

export type ApiErrorResponse = z.infer<typeof ApiErrorResponseSchema>;

// typeguard for ApiErrorResponse
export function isApiErrorResponse(
  response: unknown,
): response is ApiErrorResponse {
  return ApiErrorResponseSchema.safeParse(response).success;
}
