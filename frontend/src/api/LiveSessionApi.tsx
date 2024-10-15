import { z } from "zod";
import { userResponseSchema } from "./UserApi";

const updateContentMessage = z.object({
  type: z.literal("update_content"),
  content: z.string(),
});

const statusMessage = z.object({
  type: z.literal("status"),
  sessionId: z.string(),
  consoleContent: z.string(),
  observers: z.array(userResponseSchema),
});

export { updateContentMessage, statusMessage };
