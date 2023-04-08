import { z } from "zod";
import { ExecutionRequest } from "../routes/AddRequestForm";

const requestUrl = "http://localhost:8080/execution-requests/";

const ExecutionRequestPayload = z.object({
  //issueLink: z.string().url(),
  title: z.string().min(1),
  description: z.string(),
  statement: z.string().min(1),
  readOnly: z.boolean(),
  datasourceConnectionId: z.string().min(1),
  //confidential: z.boolean(),
});

type ExecutionRequestPayload = z.infer<typeof ExecutionRequestPayload>;

const addRequest = async (payload: ExecutionRequest): Promise<boolean> => {
  const mappedPayload: ExecutionRequestPayload = {
    title: payload.title,
    description: payload.description,
    statement: payload.statement,
    readOnly: payload.readOnly,
    datasourceConnectionId: payload.connection,
  };
  const response = await fetch(requestUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(mappedPayload),
  });
  return true;
};

export { addRequest };
