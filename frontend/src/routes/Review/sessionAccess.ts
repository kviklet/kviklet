import {
  Execute,
  ExecutionRequestResponseWithComments,
} from "../../api/ExecutionRequestApi";

const formatTime = (timestamp: number) =>
  new Date(timestamp).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
  });

// Match the backend: the first non-dry-run execution starts the access window.
// `label` is the single line the sidebar shows under the request type; the
// status pill carries the state itself, so the label never repeats it.
export function sessionAccess(
  request: ExecutionRequestResponseWithComments,
  liveEvents: Execute[],
  now: number,
) {
  const executions = [...request.events, ...liveEvents].filter(
    (event) => event._type === "EXECUTE" && !event.isDryRun,
  );
  const firstExecution = executions.length
    ? Math.min(...executions.map((event) => event.createdAt.getTime()))
    : undefined;
  const duration = request.temporaryAccessDuration;
  const expiresAt =
    firstExecution !== undefined && duration != null
      ? firstExecution + duration * 60_000
      : undefined;
  const expired =
    request.executionStatus === "EXECUTED" ||
    (expiresAt !== undefined && now >= expiresAt);
  if (expired) {
    return {
      expired,
      expiresAt,
      label:
        expiresAt !== undefined ? `Expired at ${formatTime(expiresAt)}` : "",
    };
  }
  if (duration == null) return { expired, expiresAt, label: "No expiry" };
  if (request.reviewStatus !== "APPROVED")
    return { expired, expiresAt, label: `Valid for ${duration} minutes` };
  if (expiresAt === undefined)
    return {
      expired,
      expiresAt,
      label: `${duration} minutes · starts with the first query`,
    };
  const minutes = Math.min(duration, Math.ceil((expiresAt - now) / 60_000));
  return {
    expired,
    expiresAt,
    label: `${minutes} min left · expires at ${formatTime(expiresAt)}`,
  };
}
