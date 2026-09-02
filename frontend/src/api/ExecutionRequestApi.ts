import { z } from "zod";
import { userResponseSchema } from "./UserApi";
import {
  connectionResponseSchema,
  databaseConnectionResponseSchema,
  DatabaseType,
} from "./DatasourceApi";
import { roleResponseSchema } from "./RoleApi";
import baseUrl, { apiFetch } from "./base";
import {
  ApiErrorResponse,
  ApiResponse,
  fetchWithErrorHandling,
  isApiErrorResponse,
} from "./Errors";
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
    action: z.enum(["APPROVE", "REQUEST_CHANGE", "REJECT", "CLOSE"]),
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
    previousAccessDuration: z.number().optional().nullable(),
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
  columns: z
    .array(
      z.object({
        label: z.string(),
        typeName: z.string(),
        typeClass: z.string(),
      }),
    )
    .optional()
    .nullable(),
  storedRows: z.array(z.record(z.coerce.string())).optional().nullable(),
  storedRowCount: z.number().optional().nullable(),
});

const KubernetesOutputResultLog = z.object({
  type: z.literal("KUBERNETES_OUTPUT"),
  exitCode: z.number().optional().nullable(),
  storedOutput: z.string().optional().nullable(),
  storedErrors: z.string().optional().nullable(),
  outputTruncated: z.boolean().optional(),
});

const ResultLog = z.union([
  ErrorResultLog,
  UpdateResultLog,
  QueryResultLog,
  KubernetesOutputResultLog,
]);

const ExecuteEvent = withType(
  z.object({
    type: z.literal("EXECUTE"),
    author: userResponseSchema.optional(),
    query: z.string().optional().nullable(),
    results: z.array(ResultLog).optional(),
    command: z.string().optional().nullable(),
    podName: z.string().optional().nullable(),
    namespace: z.string().optional().nullable(),
    containerName: z.string().optional().nullable(),
    createdAt: z.coerce.date(),
    id: z.string(),
    isDownload: z.boolean().optional(),
    isDump: z.boolean().optional(),
    isDryRun: z.boolean().optional(),
  }),
  "EXECUTE",
);

const RoleApprovalProgressSchema = z.object({
  role: roleResponseSchema,
  numRequired: z.number(),
  numCurrent: z.number(),
  approverNames: z.array(z.string()),
});

const ApprovalProgressSchema = z.object({
  totalRequired: z.number(),
  totalCurrent: z.number(),
  roleProgress: z.array(RoleApprovalProgressSchema),
  changeRequestedBy: z.array(z.string()),
});

const RawDatasourceRequestSchema = z.object({
  id: z.string(),
  type: z.enum(["TemporaryAccess", "SingleExecution", "Dump"]),
  author: userResponseSchema,
  title: z.string().min(1),
  description: z.string(),
  statement: z.string().nullable().optional(),
  connection: databaseConnectionResponseSchema,
  executionStatus: z.string(),
  reviewStatus: z.string(),
  createdAt: z.coerce.date(),
  connectionName: z.string().optional(),
  temporaryAccessDuration: z.number().nullable(),
  approvalProgress: ApprovalProgressSchema.optional(),
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
  command: z.string().optional().nullable(),
  temporaryAccessDuration: z.number().optional().nullable(),
  approvalProgress: ApprovalProgressSchema.optional(),
});

const ProxyResponse = z.object({
  host: z.string().nullable(),
  port: z.number(),
  username: z.string(),
  password: z.string(),
  databaseName: z.string(),
  type: z.nativeEnum(DatabaseType),
  // Not z.coerce.date().nullable(): coercion runs first and turns null into the 1970 epoch,
  // but a session without a time limit really does send null.
  expiresAt: z
    .string()
    .nullable()
    .transform((value) => (value ? new Date(value) : null)),
});

const ChangeExecutionRequestPayloadSchema = z.object({
  title: z.string().min(1).optional(),
  description: z.string().optional(),
  statement: z.string().optional(),
  command: z.string().optional(),
  podName: z.string().optional(),
  namespace: z.string().optional(),
  containerName: z.string().optional(),
  temporaryAccessDuration: z.number().optional().nullable(),
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

/**
 * What the current user may do to this request, resolved by the backend against the request itself
 * — so it already accounts for the connection the policy is scoped to, authorship, and whether the
 * request is executable yet. It does not cover the service-level rules the UI already knows about,
 * such as not being able to review your own request.
 */
const requestPermissionsSchema = z.array(z.string());

const DatasourceExecutionRequestResponseWithCommentsSchema = withType(
  RawDatasourceRequestSchema.extend({
    events: z.array(
      z.union([ReviewEvent, CommentEvent, EditEvent, ExecuteEvent]),
    ),
    permissions: requestPermissionsSchema,
  }),
  "DATASOURCE",
);

const KubernetesExecutionRequestResponseWithCommentsSchema = withType(
  RawKubernetesRequestSchema.extend({
    events: z.array(
      z.union([ReviewEvent, CommentEvent, EditEvent, ExecuteEvent]),
    ),
    permissions: requestPermissionsSchema,
  }),
  "KUBERNETES",
);

const ExecutionRequestResponseWithCommentsSchema = z.union([
  KubernetesExecutionRequestResponseWithCommentsSchema,
  DatasourceExecutionRequestResponseWithCommentsSchema,
]);

const ExecutionRequestListResponseSchema = z.object({
  requests: z.array(ExecutionRequestResponseSchema),
  hasMore: z.boolean(),
  cursor: z.coerce.date().nullable(),
});

type DatasourceExecutionRequestResponseWithComments = z.infer<
  typeof DatasourceExecutionRequestResponseWithCommentsSchema
>;
type KubernetesExecutionRequestResponseWithComments = z.infer<
  typeof KubernetesExecutionRequestResponseWithCommentsSchema
>;
type ExecutionRequestResponseWithComments = z.infer<
  typeof ExecutionRequestResponseWithCommentsSchema
>;
type ExecutionRequestListResponse = z.infer<
  typeof ExecutionRequestListResponseSchema
>;
type ExecutionRequestResponse = z.infer<typeof ExecutionRequestResponseSchema>;
type RoleApprovalProgress = z.infer<typeof RoleApprovalProgressSchema>;
type ApprovalProgress = z.infer<typeof ApprovalProgressSchema>;

type ChangeExecutionRequestPayload = z.infer<
  typeof ChangeExecutionRequestPayloadSchema
>;
type Edit = z.infer<typeof EditEvent>;
type Review = z.infer<typeof ReviewEvent>;
type Comment = z.infer<typeof CommentEvent>;
type Execute = z.infer<typeof ExecuteEvent>;
type Event = Edit | Review | Comment | Execute;
type ProxyResponse = z.infer<typeof ProxyResponse>;

const addRequest = async (
  payload: ExecutionRequest,
): Promise<ApiResponse<ExecutionRequestResponse>> => {
  return fetchWithErrorHandling(
    requestUrl,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    ExecutionRequestResponseSchema,
  );
};

const patchRequest = async (
  id: string,
  payload: ChangeExecutionRequestPayload,
): Promise<ApiResponse<ExecutionRequestResponseWithComments>> => {
  return fetchWithErrorHandling(
    requestUrl + id,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(payload),
    },
    ExecutionRequestResponseWithCommentsSchema,
  );
};

const postStartServer = async (
  id: string,
): Promise<ApiResponse<ProxyResponse>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/proxy",
    {
      method: "POST",
      credentials: "include",
    },
    ProxyResponse,
  );
};

interface GetRequestsParams {
  reviewStatuses?: string[];
  executionStatuses?: string[];
  connectionIds?: string[];
  authorId?: string;
  createdAfter?: Date;
  createdBefore?: Date;
  after?: Date;
  limit?: number;
}

const getRequestsPaginated = async (
  params?: GetRequestsParams,
): Promise<ApiResponse<ExecutionRequestListResponse>> => {
  const searchParams = new URLSearchParams();

  if (params?.reviewStatuses && params.reviewStatuses.length > 0) {
    params.reviewStatuses.forEach((status) =>
      searchParams.append("reviewStatuses", status),
    );
  }

  if (params?.executionStatuses && params.executionStatuses.length > 0) {
    params.executionStatuses.forEach((status) =>
      searchParams.append("executionStatuses", status),
    );
  }

  if (params?.connectionIds && params.connectionIds.length > 0) {
    params.connectionIds.forEach((id) =>
      searchParams.append("connectionIds", id),
    );
  }

  if (params?.authorId) {
    searchParams.append("authorId", params.authorId);
  }

  if (params?.createdAfter) {
    searchParams.append("createdAfter", params.createdAfter.toISOString());
  }

  if (params?.createdBefore) {
    searchParams.append("createdBefore", params.createdBefore.toISOString());
  }

  if (params?.after) {
    searchParams.append("after", params.after.toISOString());
  }

  if (params?.limit) {
    searchParams.append("limit", params.limit.toString());
  }

  const url = searchParams.toString()
    ? `${requestUrl}?${searchParams.toString()}`
    : requestUrl;

  return fetchWithErrorHandling(
    url,
    {
      method: "GET",
      credentials: "include",
    },
    ExecutionRequestListResponseSchema,
  );
};

const getSingleRequest = async (
  id: string,
): Promise<ApiResponse<ExecutionRequestResponseWithComments>> => {
  return fetchWithErrorHandling(
    requestUrl + id,
    {
      method: "GET",
      credentials: "include",
    },
    ExecutionRequestResponseWithCommentsSchema,
  );
};

const addCommentToRequest = async (
  id: string,
  comment: string,
): Promise<ApiResponse<Event>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/comments",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ comment }),
    },
    z.union([CommentEvent, ReviewEvent]),
  );
};

const addReviewToRequest = async (
  id: string,
  review: string,
  action: string,
): Promise<ApiResponse<Review>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/reviews",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ comment: review, action }),
    },
    ReviewEvent,
  );
};

const closeRequest = async (
  id: string,
  comment: string = "",
): Promise<ApiResponse<Review>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/close",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ comment }),
    },
    ReviewEvent,
  );
};

const streamDump = async (
  executionRequestId: string,
): Promise<ReadableStream<Uint8Array>> => {
  // Request SQL dump data from the backend which returns an array of encoded strings in chunks
  const response = await apiFetch(`${requestUrl}${executionRequestId}/dump`, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    throw new Error(
      `Failed to fetch SQL dump: ${response.status} ${response.statusText}`,
    );
  }
  if (response.body === null) throw new Error("Response body is null");
  return response.body;
};

// Executes the request and downloads the results as a file, keeping errors (e.g. a
// missing execute permission) in-app instead of navigating the tab to raw JSON.
const downloadResults = async (
  executionRequestId: string,
  query?: string,
): Promise<void> => {
  const downloadUrl =
    `${requestUrl}${executionRequestId}/download` +
    (query ? `?query=${encodeURIComponent(query)}` : "");
  const response = await apiFetch(downloadUrl, {
    method: "GET",
    credentials: "include",
  });

  if (!response.ok) {
    const json: unknown = await response.json().catch(() => null);
    if (isApiErrorResponse(json)) {
      throw new Error(json.message);
    }
    throw new Error(`Download failed: ${response.statusText}`);
  }

  const blob = await response.blob();
  const url = window.URL.createObjectURL(blob);
  const contentDisposition = response.headers.get("Content-Disposition");
  const filenameMatch = contentDisposition?.match(/filename="(.+)"/);
  const filename = filenameMatch ? filenameMatch[1] : "results.csv";

  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  window.URL.revokeObjectURL(url);
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

const DocumentsResponseSchema = withType(
  z.object({
    documents: z.array(z.record(z.unknown())),
  }),
  "documents",
);

const CancelQueryResponseSchema = z.object({
  success: z.boolean(),
});

type CancelQueryResponse = z.infer<typeof CancelQueryResponseSchema>;

const DBExecuteResponseResultSchema = z.union([
  UpdateExecuteResponseSchema,
  SelectExecuteResponseSchema,
  ErrorResponseSchema,
  DocumentsResponseSchema,
]);

const DBExecuteResponseSchema = z.object({
  results: z.array(DBExecuteResponseResultSchema),
});

const KubernetesExecuteResponseSchema = z.object({
  errors: z.array(z.string()),
  messages: z.array(z.string()),
  finished: z.boolean(),
  exitCode: z.number().nullable().optional(),
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

const runQuery = async (
  id: string,
  query?: string,
  explain: boolean = false,
  dryRun: boolean = false,
): Promise<ApiResponse<QueryResult>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/execute",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify({ query, explain, dryRun }),
    },
    DBExecuteResponseSchema,
  );
};

const cancel = async (
  id: string,
): Promise<ApiResponse<CancelQueryResponse>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/cancel",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    CancelQueryResponseSchema,
  );
};

const executeCommand = async (
  id: string,
): Promise<ApiResponse<KubernetesExecuteResponse>> => {
  return fetchWithErrorHandling(
    requestUrl + id + "/execute",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
    },
    KubernetesExecuteResponseSchema,
  );
};

export {
  addRequest,
  getRequestsPaginated,
  getSingleRequest,
  addCommentToRequest,
  addReviewToRequest,
  closeRequest,
  runQuery,
  cancel,
  patchRequest,
  postStartServer,
  executeCommand,
  streamDump,
  downloadResults,
  DBExecuteResponseResultSchema,
};

export type {
  DBExecuteResponseResult as ExecuteResponseResult,
  ExecutionRequestResponse,
  ExecutionRequestListResponse,
  GetRequestsParams,
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
  RoleApprovalProgress,
  ApprovalProgress,
};
