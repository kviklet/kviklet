import { z } from "zod";
import { userResponseSchema } from "./UserApi";
import { DBExecuteResponseResultSchema } from "./ExecutionRequestApi";

// Helper function to transform 'type' to '_type' for consistency with ExecutionRequestApi
function withType<T, U extends string>(schema: z.ZodSchema<T>, typeValue: U) {
  return schema.transform((data) => ({
    ...data,
    _type: typeValue,
  })) as z.ZodSchema<T & { _type: U }>;
}

// Schema for Execute event returned in WebSocket messages
const executeEventResponseSchema = withType(
  z.object({
    type: z.literal("EXECUTE"),
    id: z.string(),
    author: userResponseSchema.optional(),
    createdAt: z.coerce.date(),
    query: z.string().optional().nullable(),
    results: z.array(z.any()).optional(), // ResultLog array
    command: z.string().optional().nullable(),
    containerName: z.string().optional().nullable(),
    podName: z.string().optional().nullable(),
    namespace: z.string().optional().nullable(),
    isDownload: z.boolean().optional(),
    isDump: z.boolean().optional(),
  }),
  "EXECUTE",
);

const updateContentMessage = z.object({
  type: z.literal("update_content"),
  content: z.string(),
  ref: z.string(),
});

const executeStatementMessage = z.object({
  type: z.literal("execute"),
  statement: z.string(),
});

const cancelMessage = z.object({
  type: z.literal("cancel"),
});

const statusMessage = z.object({
  type: z.literal("status"),
  sessionId: z.string(),
  consoleContent: z.string(),
  observers: z.array(userResponseSchema),
  ref: z.string(),
});

const resultMessage = z.object({
  type: z.literal("result"),
  sessionId: z.string(),
  results: z.array(DBExecuteResponseResultSchema),
  event: executeEventResponseSchema.optional(),
});

const errorMessage = z.object({
  type: z.literal("error"),
  sessionId: z.string(),
  error: z.string(),
});

const responseMessage = z.discriminatedUnion("type", [
  statusMessage,
  resultMessage,
  errorMessage,
]);

export {
  updateContentMessage,
  statusMessage,
  executeStatementMessage,
  cancelMessage,
  resultMessage,
  errorMessage,
  responseMessage,
};
