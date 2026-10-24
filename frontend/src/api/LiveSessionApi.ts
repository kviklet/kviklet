import { z } from "zod";
import { userResponseSchema } from "./UserApi";
import { DBExecuteResponseSchema, ExecuteEvent } from "./ExecutionRequestApi";

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

const resultMessage = DBExecuteResponseSchema.extend({
  type: z.literal("result"),
  sessionId: z.string(),
  event: ExecuteEvent.optional(),
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
