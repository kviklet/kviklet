import { ZodSchema, ZodTypeDef, z } from "zod";

export const ApiErrorResponseSchema = z.object({
  message: z.string(),
});

export type ApiErrorResponse = z.infer<typeof ApiErrorResponseSchema>;

export function isApiErrorResponse(
  response: unknown,
): response is ApiErrorResponse {
  return ApiErrorResponseSchema.safeParse(response).success;
}

export const parseSchemaOrError = <Output, Input = Output>(
  schema: ZodSchema<Output, ZodTypeDef, Input>,
  data: unknown,
): Output | ApiErrorResponse => {
  const result = z.union([schema, ApiErrorResponseSchema]).safeParse(data);
  if (result.success) {
    return result.data;
  } else {
    console.error(result.error);
    return {
      message: "Invalid server response.",
    };
  }
};

export type ApiResponse<T> = T | ApiErrorResponse;

export async function fetchWithErrorHandling<Output, Input = Output>(
  url: string,
  options: RequestInit,
  schema: ZodSchema<Output, ZodTypeDef, Input>,
): Promise<ApiResponse<Output>> {
  try {
    const response = await fetch(url, options);
    const json: unknown = await response.json();

    return parseSchemaOrError(schema, json);
  } catch (error) {
    if (error instanceof Error) {
      return { message: error.message };
    }
    return { message: "An unknown error occurred" };
  }
}

async function fetchEmptyWithErrorHandling(
  url: string,
  options: RequestInit,
): Promise<ApiErrorResponse | null> {
  try {
    const response = await fetch(url, options);
    if (
      response.status === 204 ||
      response.headers.get("Content-Length") === "0"
    ) {
      return null;
    }
    const json: unknown = await response.json();

    return parseSchemaOrError(ApiErrorResponseSchema, json);
  } catch (error) {
    if (error instanceof Error) {
      return { message: error.message };
    }
    return { message: "An unknown error occurred" };
  }
}

export { fetchEmptyWithErrorHandling };
