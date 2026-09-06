import {
  Execute,
  ExecutionRequestResponseWithComments,
} from "../../api/ExecutionRequestApi";

// Match the backend: the first non-dry-run execution starts the access window.
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
  const expiresAt =
    firstExecution !== undefined && request.temporaryAccessDuration != null
      ? firstExecution + request.temporaryAccessDuration * 60_000
      : undefined;
  const expired =
    request.executionStatus === "EXECUTED" ||
    (expiresAt !== undefined && now >= expiresAt);
  if (expired) return { expired, label: "Access expired", expiresAt };
  if (request.reviewStatus === "REJECTED")
    return { expired, label: "Request closed", expiresAt };
  if (request.reviewStatus === "CHANGE_REQUESTED")
    return { expired, label: "Changes requested", expiresAt };
  if (request.reviewStatus !== "APPROVED")
    return { expired, label: "Awaiting approval", expiresAt };
  if (request.temporaryAccessDuration == null)
    return { expired, label: "No expiry", expiresAt };
  if (expiresAt === undefined)
    return {
      expired,
      label: `${request.temporaryAccessDuration} minutes of access · Starts with your first query`,
      expiresAt,
    };
  const minutes = Math.min(
    request.temporaryAccessDuration,
    Math.ceil((expiresAt - now) / 60_000),
  );
  return {
    expired,
    label: `${minutes} ${minutes === 1 ? "minute" : "minutes"} remaining`,
    expiresAt,
  };
}
