import { z } from "zod";
import { ExecutionRequest } from "../routes/NewRequest";
import { userResponseSchema } from "./UserApi";
import { connectionResponseSchema } from "./DatasourceApi";
import baseUrl from "./base";

const requestUrl = `${baseUrl}/execution-requests/`;

const DateTime = z.preprocess(
  (val) => (typeof val == "string" ? val.concat("Z") : undefined),
  z.string().datetime(),
);

const ExecutionRequestPayload = z
  .object({
    //issueLink: z.string().url(),
    title: z.string().min(1),
    description: z.string(),
    type: z.enum(["TemporaryAccess", "SingleQuery"]),
    statement: z.coerce.string().nullable().optional(),
    readOnly: z.boolean(),
    datasourceConnectionId: z.string().min(1),
    //confidential: z.boolean(),
  })
  .refine(
    (data) =>
      data.type === "TemporaryAccess" ||
      (!!data.statement && data.type === "SingleQuery"),
    {
      message: "If you create a query request an SQL statement is rquired",
    },
  );

const CommentEvent = withType(
  z.object({
    author: userResponseSchema.optional(),
    comment: z.string(),
    createdAt: DateTime,
    id: z.string(),
  }),
  "COMMENT",
);

const ReviewEvent = withType(
  z.object({
    author: userResponseSchema.optional(),
    comment: z.string(),
    createdAt: DateTime,
    action: z.enum(["APPROVE", "COMMENT", "REQUEST_CHANGE"]),
    id: z.string(),
  }),
  "REVIEW",
);

const EditEvent = withType(
  z.object({
    author: userResponseSchema.optional(),
    previousQuery: z.string(),
    createdAt: DateTime,
    id: z.string(),
  }),
  "EDIT",
);

const ExecutionRequestResponse = z.object({
  id: z.string(),
  type: z.enum(["TemporaryAccess", "SingleQuery"]),
  author: userResponseSchema,
  title: z.string().min(1),
  description: z.string(),
  statement: z.string().nullable().optional(),
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
  statement: z.string().optional(),
  readOnly: z.boolean().optional(),
});

const ExecutionRequestResponseWithComments = ExecutionRequestResponse.extend({
  events: z.array(z.union([ReviewEvent, CommentEvent, EditEvent])),
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
type Edit = z.infer<typeof EditEvent>;
type Review = z.infer<typeof ReviewEvent>;
type Comment = z.infer<typeof CommentEvent>;
type Event = Edit | Review | Comment;

const addRequest = async (payload: ExecutionRequest): Promise<boolean> => {
  const mappedPayload: ExecutionRequestPayload = {
    title: payload.title,
    description: payload.description,
    statement: payload.statement,
    type: payload.type,
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
  const event = z.union([CommentEvent, ReviewEvent]).parse(json);
  return event;
};

const addReviewToRequest = async (
  id: string,
  review: string,
  action: string,
) => {
  const response = await fetch(requestUrl + id + "/reviews", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify({ comment: review, action }),
  });
  await response.json();

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

const ExecuteResponseResultSchema = z.union([
  UpdateExecuteResponseSchema,
  SelectExecuteResponseSchema,
  ErrorResponseSchema,
]);

const ExecuteResponseSchema = z.object({
  results: z.array(ExecuteResponseResultSchema),
});

type ExecuteResponseResult = z.infer<typeof ExecuteResponseResultSchema>;
type ExecuteResponse = z.infer<typeof ExecuteResponseSchema>;
type UpdateExecuteResponse = z.infer<typeof UpdateExecuteResponseSchema>;
type SelectExecuteResponse = z.infer<typeof SelectExecuteResponseSchema>;
type ErrorResponse = z.infer<typeof ErrorResponseSchema>;
type Column = z.infer<typeof ColumnSchema>;

const runQuery = async (
  id: string,
  query?: string,
): Promise<ExecuteResponseResult> => {
  const response = await fetch(requestUrl + id + "/execute", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    ...(query && { body: JSON.stringify({ query }) }),
  });
  const json: unknown = await response.json();
  const result = ExecuteResponseSchema.parse(json);
  return result.results[result.results.length - 1];
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
  Edit,
  Review,
  Comment,
  Event,
  Column,
};
