import { ZodSchema, z } from "zod";

export const ApiErrorResponseSchema = z.object({
  message: z.string(),
});

export type ApiErrorResponse = z.infer<typeof ApiErrorResponseSchema>;

export function isApiErrorResponse(
  response: unknown,
): response is ApiErrorResponse {
  return ApiErrorResponseSchema.safeParse(response).success;
}

export const parseSchemaOrError = <T>(
  schema: ZodSchema<T>,
  data: unknown,
): T | ApiErrorResponse => {
  // First check if it's already an error response
  const errorResult = ApiErrorResponseSchema.safeParse(data);
  if (errorResult.success) {
    return errorResult.data;
  }

  // Try to parse with the expected schema
  const schemaResult = schema.safeParse(data);
  if (schemaResult.success) {
    return schemaResult.data;
  } else {
    // Format the validation errors for better debugging
    const formattedErrors = schemaResult.error.format();
    console.error("Schema validation failed:", formattedErrors);

    return {
      message: `Invalid server response: ${schemaResult.error.message}`,
    };
  }
};

export type ApiResponse<T> = T | ApiErrorResponse;

export async function fetchWithErrorHandling<T>(
  url: string,
  options: RequestInit,
  schema: ZodSchema<T>,
): Promise<ApiResponse<T>> {
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
): Promise<ApiErrorResponse | void> {
  try {
    const response = await fetch(url, options);
    if (
      response.status === 204 ||
      response.headers.get("Content-Length") === "0"
    ) {
      return;
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
