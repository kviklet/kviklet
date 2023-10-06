import { z } from "zod";
import { ExecutionRequest } from "../routes/AddRequestForm";
import { userResponseSchema } from "./UserApi";
import { connectionResponseSchema } from "./DatasourceApi";
import baseUrl from "./base";

const requestUrl = `${baseUrl}/execution-requests/`;

const DateTime = z.preprocess(
  (val) => (typeof val == "string" ? val.concat("Z") : undefined),
  z.string().datetime(),
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

const Comment = withType(
  z.object({
    author: userResponseSchema.optional(),
    comment: z.string(),
    createdAt: DateTime,
    id: z.string(),
  }),
  "COMMENT",
);

const Review = withType(
  z.object({
    author: userResponseSchema.optional(),
    comment: z.string(),
    createdAt: DateTime,
    action: z.enum(["APPROVE", "COMMENT", "REQUEST_CHANGE"]),
    id: z.string(),
  }),
  "REVIEW",
);

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
  events: z.array(z.union([Comment, Review])),
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
type Event = z.infer<typeof Comment> | z.infer<typeof Review>;

const addRequest = async (payload: ExecutionRequest): Promise<boolean> => {
  const mappedPayload: ExecutionRequestPayload = {
    title: payload.title,
    description: payload.description,
    statement: payload.statement,
    readOnly: false,
    datasourceConnectionId: payload.connection,
  };
  await fetch(requestUrl, {
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
  payload: ChangeExecutionRequestPayload,
): Promise<ExecutionRequestResponseWithComments> => {
  const response = await fetch(requestUrl + id, {
    method: "PATCH",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
  });
  const json: unknown = await response.json();
  return ExecutionRequestResponseWithComments.parse(json);
};

const getRequests = async (): Promise<ExecutionRequestsResponse> => {
  const response = await fetch(requestUrl, {
    method: "GET",
    credentials: "include",
  });
  const json: unknown = await response.json();
  const requests = ExecutionRequestsResponse.parse(json);
  return requests;
};

const getSingleRequest = async (
  id: string,
): Promise<ExecutionRequestResponseWithComments | undefined> => {
  const response = await fetch(requestUrl + id, {
    method: "GET",
    credentials: "include",
  });
  if (response.status == 404) {
    return undefined;
  }
  const json: unknown = await response.json();
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
  const json: unknown = await response.json();
  const event = z.union([Comment, Review]).parse(json);
  return event;
};

const addReviewToRequest = async (
  id: string,
  review: string,
  action: string,
) => {
  await fetch(requestUrl + id + "/reviews", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify({ comment: review, action }),
  });
  return;
};

function withType<T, U extends string>(schema: z.ZodSchema<T>, typeValue: U) {
  return schema.transform((data) => ({
    ...data,
    _type: typeValue,
  })) as z.ZodSchema<T & { _type: U }>;
}

const UpdateExecuteResponseSchema = withType(
  z.object({
    rowsUpdated: z.number(),
  }),
  "update",
);

const ColumnSchema = z.object({
  label: z.string(),
  typeName: z.string(),
  typeClass: z.string(),
});

const SelectExecuteResponseSchema = withType(
  z.object({
    columns: z.array(ColumnSchema),
    data: z.array(z.record(z.string())),
  }),
  "select",
);

const ErrorResponseSchema = withType(
  z.object({
    errorCode: z.number(),
    message: z.string().optional(),
  }),
  "error",
);

const ExecuteResponseSchema = z.union([
  UpdateExecuteResponseSchema,
  SelectExecuteResponseSchema,
  ErrorResponseSchema,
]);

type ExecuteResponse = z.infer<typeof ExecuteResponseSchema>;
type UpdateExecuteResponse = z.infer<typeof UpdateExecuteResponseSchema>;
type SelectExecuteResponse = z.infer<typeof SelectExecuteResponseSchema>;
type ErrorResponse = z.infer<typeof ErrorResponseSchema>;
type Column = z.infer<typeof ColumnSchema>;

const runQuery = async (id: string): Promise<ExecuteResponse> => {
  const response = await fetch(requestUrl + id + "/execute", {
    method: "POST",
    credentials: "include",
  });
  const json: unknown = await response.json();
  const result = ExecuteResponseSchema.parse(json);
  return result;
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
  ExecuteResponse,
  UpdateExecuteResponse,
  SelectExecuteResponse,
  ErrorResponse,
  Event,
  Column,
};
