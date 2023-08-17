import { z } from "zod";
import { ExecutionRequest } from "../routes/AddRequestForm";
import { type } from "os";
import { userResponseSchema } from "./UserApi";
import { connectionResponseSchema } from "./DatasourceApi";
import baseUrl from "./base";

const requestUrl = `${baseUrl}/execution-requests/`;

const DateTime = z.preprocess(
  (val) => (typeof val == "string" ? val.concat("Z") : undefined),
  z.string().datetime()
);

const ExecutionRequestPayload = z.object({
  //issueLink: z.string().url(),
  title: z.string().min(1),
  description: z.string(),
  statement: z.string().min(1),
  readOnly: z.boolean(),
  datasourceConnectionId: z.string().min(1),
  //confidential: z.boolean(),
});

const Comment = z.object({
  author: z.string().optional(),
  comment: z.string(),
  createdAt: DateTime,
  id: z.string(),
});

const ExecutionRequestResponse = z.object({
  id: z.string(),
  author: userResponseSchema,
  title: z.string().min(1),
  description: z.string(),
  statement: z.string().min(1),
  readOnly: z.boolean(),
  connection: connectionResponseSchema,
  executionStatus: z.string(),
  reviewStatus: z.string(),
  createdAt: DateTime,
  connectionName: z.string().optional(),
});

const ChangeExecutionRequestPayload = z.object({
  title: z.string().min(1).optional(),
  description: z.string().optional(),
  statement: z.string().min(1).optional(),
  readOnly: z.boolean().optional(),
});

const ExecutionRequestResponseWithComments = ExecutionRequestResponse.extend({
  events: z.array(Comment),
});

const ExecutionRequestsResponse = z.array(ExecutionRequestResponse);

type ExecutionRequestResponseWithComments = z.infer<
  typeof ExecutionRequestResponseWithComments
>;
type ExecutionRequestResponse = z.infer<typeof ExecutionRequestResponse>;
type ExecutionRequestsResponse = z.infer<typeof ExecutionRequestsResponse>;
type ExecutionRequestPayload = z.infer<typeof ExecutionRequestPayload>;
type ChangeExecutionRequestPayload = z.infer<
  typeof ChangeExecutionRequestPayload
>;

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
    credentials: "include",
    body: JSON.stringify(mappedPayload),
  });
  return true;
};

const patchRequest = async (
  id: string,
  payload: ChangeExecutionRequestPayload
): Promise<ExecutionRequestResponseWithComments> => {
  console.log(payload);
  const response = await fetch(requestUrl + id, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const json = await response.json();
  return ExecutionRequestResponseWithComments.parse(json);
};

const getRequests = async (): Promise<ExecutionRequestsResponse> => {
  const response = await fetch(requestUrl, {
    method: "GET",
    credentials: "include",
  });
  const json = await response.json();
  console.log(json);
  const requests = ExecutionRequestsResponse.parse(json);
  return requests;
};

const getSingleRequest = async (
  id: string
): Promise<ExecutionRequestResponseWithComments | undefined> => {
  const response = await fetch(requestUrl + id, {
    method: "GET",
    credentials: "include",
  });
  if (response.status == 404) {
    return undefined;
  }
  const json = await response.json();
  console.log(json);
  const request = ExecutionRequestResponseWithComments.parse(json);
  return request;
};

const addCommentToRequest = async (id: string, comment: string) => {
  const response = await fetch(requestUrl + id + "/comments", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify({ comment }),
  });
  return;
};

const addReviewToRequest = async (
  id: string,
  review: string,
  action: string
) => {
  const response = await fetch(requestUrl + id + "/reviews", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify({ comment: review, action }),
  });
  return;
};

const runQuery = async (id: string) => {
  const response = await fetch(requestUrl + id + "/execute", {
    method: "POST",
    credentials: "include",
  });
  const json = await response.json();
  console.log(json);
  return;
};

export {
  addRequest,
  getRequests,
  getSingleRequest,
  addCommentToRequest,
  addReviewToRequest,
  runQuery,
  patchRequest,
};
export type {
  ExecutionRequestResponse,
  ExecutionRequestsResponse,
  ExecutionRequestResponseWithComments,
  ChangeExecutionRequestPayload,
};
