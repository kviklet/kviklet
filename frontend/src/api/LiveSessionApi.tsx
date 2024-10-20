import { z } from "zod";
import { userResponseSchema } from "./UserApi";
import { DBExecuteResponseResultSchema } from "./ExecutionRequestApi";

const updateContentMessage = z.object({
  type: z.literal("update_content"),
  content: z.string(),
});

const executeStatementMessage = z.object({
  type: z.literal("execute"),
  statement: z.string(),
});

const statusMessage = z.object({
  type: z.literal("status"),
  sessionId: z.string(),
  consoleContent: z.string(),
  observers: z.array(userResponseSchema),
});

const resultMessage = z.object({
  type: z.literal("result"),
  results: z.array(DBExecuteResponseResultSchema),
});

export {
  updateContentMessage,
  statusMessage,
  executeStatementMessage,
  resultMessage,
};
