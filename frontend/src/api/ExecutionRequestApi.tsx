import { z } from "zod";
import { userResponseSchema } from "./UserApi";
import { connectionResponseSchema } from "./DatasourceApi";
import baseUrl from "./base";
import { ApiErrorResponse, ApiErrorResponseSchema } from "./Errors";
import { ExecutionRequest } from "../routes/NewRequest";

const requestUrl = `${baseUrl}/execution-requests/`;

const CommentEvent = withType(
  z.object({
    author: userResponseSchema.optional(),
    comment: z.string(),
    createdAt: z.coerce.date(),
    id: z.string(),
  }),
  "COMMENT",
);

const ReviewEvent = withType(
  z.object({
    type: z.literal("REVIEW"),
    author: userResponseSchema.optional(),
    comment: z.string(),
    createdAt: z.coerce.date(),
    action: z.enum(["APPROVE", "COMMENT", "REQUEST_CHANGE"]),
    id: z.string(),
  }),
  "REVIEW",
);

const EditEvent = withType(
  z.object({
    type: z.literal("EDIT"),
    author: userResponseSchema.optional(),
    previousQuery: z.string().optional().nullable(),
    previousCommand: z.string().optional().nullable(),
    previousPodName: z.string().optional().nullable(),
    previousNamespace: z.string().optional().nullable(),
    previousContainerName: z.string().optional().nullable(),
    createdAt: z.coerce.date(),
    id: z.string(),
  }),
  "EDIT",
);

const ErrorResultLog = z.object({
  type: z.literal("ERROR"),
  errorCode: z.number(),
  message: z.string(),
});

const UpdateResultLog = z.object({
  type: z.literal("UPDATE"),
  rowsUpdated: z.number(),
});

const QueryResultLog = z.object({
  type: z.literal("QUERY"),
  columnCount: z.number(),
  rowCount: z.number(),
});

const ResultLog = z.union([ErrorResultLog, UpdateResultLog, QueryResultLog]);

const ExecuteEvent = withType(
  z.object({
    type: z.literal("EXECUTE"),
    author: userResponseSchema.optional(),
    query: z.string().optional().nullable(),
    results: z.array(ResultLog),
    command: z.string().optional().nullable(),
    podName: z.string().optional().nullable(),
    namespace: z.string().optional().nullable(),
    containerName: z.string().optional().nullable(),
    createdAt: z.coerce.date(),
    id: z.string(),
  }),
  "EXECUTE",
);

const RawDatasourceRequestSchema = z.object({
  id: z.string(),
  type: z.enum(["TemporaryAccess", "SingleExecution"]),
  author: userResponseSchema,
  title: z.string().min(1),
  description: z.string(),
  statement: z.string().nullable().optional(),
  connection: connectionResponseSchema,
  executionStatus: z.string(),
  reviewStatus: z.string(),
  createdAt: z.coerce.date(),
  connectionName: z.string().optional(),
});

const RawKubernetesRequestSchema = z.object({
  id: z.string(),
  type: z.enum(["TemporaryAccess", "SingleExecution"]),
  author: userResponseSchema,
  title: z.string().min(1),
  description: z.string(),
  connection: connectionResponseSchema,
  executionStatus: z.string(),
  reviewStatus: z.string(),
  createdAt: z.coerce.date(),
  connectionName: z.string().optional(),
  podName: z.string(),
  namespace: z.string(),
  containerName: z.coerce.string(),
  command: z.string().optional(),
});

const ProxyResponse = z.object({
  port: z.number(),
  username: z.string(),
  password: z.string(),
});

const ChangeExecutionRequestPayloadSchema = z.object({
  title: z.string().min(1).optional(),
  description: z.string().optional(),
  statement: z.string().optional(),
  readOnly: z.boolean().optional(),
  command: z.string().optional(),
  podName: z.string().optional(),
  namespace: z.string().optional(),
  containerName: z.string().optional(),
});

const DatasourceExecutionRequestResponse = withType(
  RawDatasourceRequestSchema,
  "DATASOURCE",
);

const KubernetesExecutionRequestResponse = withType(
  RawKubernetesRequestSchema,
  "KUBERNETES",
);

const ExecutionRequestResponseSchema = z.union([
  KubernetesExecutionRequestResponse,
  DatasourceExecutionRequestResponse,
]);

const DatasourceExecutionRequestResponseWithCommentsSchema = withType(
  RawDatasourceRequestSchema.extend({
    events: z.array(
      z.union([ReviewEvent, CommentEvent, EditEvent, ExecuteEvent]),
    ),
  }),
  "DATASOURCE",
);

const KubernetesExecutionRequestResponseWithCommentsSchema = withType(
  RawKubernetesRequestSchema.extend({
    events: z.array(
      z.union([ReviewEvent, CommentEvent, EditEvent, ExecuteEvent]),
    ),
  }),
  "KUBERNETES",
);

const ExecutionRequestResponseWithCommentsSchema = z.union([
  KubernetesExecutionRequestResponseWithCommentsSchema,
  DatasourceExecutionRequestResponseWithCommentsSchema,
]);

const ExecutionRequestsResponseSchema = z.array(ExecutionRequestResponseSchema);

type DatasourceExecutionRequestResponseWithComments = z.infer<
  typeof DatasourceExecutionRequestResponseWithCommentsSchema
>;
type KubernetesExecutionRequestResponseWithComments = z.infer<
  typeof KubernetesExecutionRequestResponseWithCommentsSchema
>;
type ExecutionRequestResponseWithComments = z.infer<
  typeof ExecutionRequestResponseWithCommentsSchema
>;
type ExecutionRequestsResponse = z.infer<
  typeof ExecutionRequestsResponseSchema
>;
type ExecutionRequestResponse = z.infer<typeof ExecutionRequestResponseSchema>;

type ChangeExecutionRequestPayload = z.infer<
  typeof ChangeExecutionRequestPayloadSchema
>;
type Edit = z.infer<typeof EditEvent>;
type Review = z.infer<typeof ReviewEvent>;
type Comment = z.infer<typeof CommentEvent>;
type Execute = z.infer<typeof ExecuteEvent>;
type Event = Edit | Review | Comment | Execute;
type ProxyResponse = z.infer<typeof ProxyResponse>;

const addRequest = async (payload: ExecutionRequest): Promise<boolean> => {
  await fetch(requestUrl, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify(payload),
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
  return ExecutionRequestResponseWithCommentsSchema.parse(json);
};

const postStartServer = async (id: string): Promise<ProxyResponse> => {
  const response = await fetch(requestUrl + id + "/proxy", {
    method: "POST",
    credentials: "include",
  });
  const json: unknown = await response.json();
  const proxy = ProxyResponse.parse(json);
  return proxy;
};

const getRequests = async (): Promise<ExecutionRequestsResponse> => {
  const response = await fetch(requestUrl, {
    method: "GET",
    credentials: "include",
  });
  const json: unknown = await response.json();
  const requests = ExecutionRequestsResponseSchema.parse(json);
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
  const request = ExecutionRequestResponseWithCommentsSchema.parse(json);
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
    data: z.array(z.record(z.coerce.string())),
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

const DBExecuteResponseResultSchema = z.union([
  UpdateExecuteResponseSchema,
  SelectExecuteResponseSchema,
  ErrorResponseSchema,
]);

const DBExecuteResponseSchema = z.object({
  results: z.array(DBExecuteResponseResultSchema),
});

const KubernetesExecuteResponseSchema = z.object({
  errors: z.array(z.string()),
  messages: z.array(z.string()),
  finished: z.boolean(),
  exitCode: z.number().optional(),
});

type DBExecuteResponseResult = z.infer<typeof DBExecuteResponseResultSchema>;
type KubernetesExecuteResponse = z.infer<
  typeof KubernetesExecuteResponseSchema
>;
type DBExecuteResponse = z.infer<typeof DBExecuteResponseSchema>;
type UpdateExecuteResponse = z.infer<typeof UpdateExecuteResponseSchema>;
type SelectExecuteResponse = z.infer<typeof SelectExecuteResponseSchema>;
type ErrorResponse = z.infer<typeof ErrorResponseSchema>;
type Column = z.infer<typeof ColumnSchema>;

type QueryResult = {
  results?: DBExecuteResponseResult[];
  error?: ApiErrorResponse;
};

type KubernetesResponse = {
  results?: KubernetesExecuteResponse;
  error?: ApiErrorResponse;
};

const runQuery = async (
  id: string,
  query?: string,
  explain: boolean = false,
): Promise<QueryResult> => {
  const response = await fetch(requestUrl + id + "/execute", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
    body: JSON.stringify({ query, explain }),
  });
  const json: unknown = await response.json();
  if (response.ok) {
    const result = DBExecuteResponseSchema.parse(json);
    return {
      results: result.results,
    };
  } else {
    const result = ApiErrorResponseSchema.parse(json);
    return { error: result };
  }
};

const executeCommand = async (id: string): Promise<KubernetesResponse> => {
  const response = await fetch(requestUrl + id + "/execute", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    credentials: "include",
  });
  const json: unknown = await response.json();
  if (response.ok) {
    const result = KubernetesExecuteResponseSchema.parse(json);
    return { results: result };
  } else {
    const result = ApiErrorResponseSchema.parse(json);
    return { error: result };
  }
};

export {
  addRequest,
  getRequests,
  getSingleRequest,
  addCommentToRequest,
  addReviewToRequest,
  runQuery,
  patchRequest,
  postStartServer,
  executeCommand,
};
export type {
  DBExecuteResponseResult as ExecuteResponseResult,
  ExecutionRequestResponse,
  ExecutionRequestsResponse,
  DatasourceExecutionRequestResponseWithComments,
  KubernetesExecutionRequestResponseWithComments,
  ExecutionRequestResponseWithComments,
  ChangeExecutionRequestPayload,
  DBExecuteResponse as ExecuteResponse,
  UpdateExecuteResponse,
  SelectExecuteResponse,
  ErrorResponse,
  Edit,
  Review,
  Comment,
  Event,
  Execute,
  Column,
  ApiErrorResponse,
  QueryResult,
  ProxyResponse,
  KubernetesExecuteResponse,
};
