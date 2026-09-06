import { describe, expect, it } from "vitest";
import {
  Execute,
  ExecutionRequestResponseWithComments,
} from "../../api/ExecutionRequestApi";
import { sessionAccess } from "./sessionAccess";

const start = Date.parse("2026-09-07T12:00:00Z");
const request = {
  type: "TemporaryAccess",
  reviewStatus: "APPROVED",
  executionStatus: "EXECUTABLE",
  temporaryAccessDuration: 60,
  events: [],
} as unknown as ExecutionRequestResponseWithComments;
const execution = (createdAt: number, isDryRun = false): Execute => ({
  _type: "EXECUTE",
  type: "EXECUTE",
  id: String(createdAt),
  createdAt: new Date(createdAt),
  isDryRun,
});

describe("temporary access window", () => {
  it("does not start when an approved workspace is opened", () => {
    expect(sessionAccess(request, [], start)).toEqual({
      expired: false,
      expiresAt: undefined,
      label: "60 minutes of access · Starts with your first query",
    });
  });
  it("uses the earliest persisted or live execution, excluding dry runs", () => {
    const access = sessionAccess(
      {
        ...request,
        events: [execution(start), execution(start - 60_000, true)],
      },
      [execution(start + 60_000)],
      start + 30 * 60_000,
    );
    expect(access.expiresAt).toBe(start + 60 * 60_000);
    expect(access.label).toBe("30 minutes remaining");
  });
  it("expires at the deadline without waiting for a refresh", () => {
    expect(
      sessionAccess(request, [execution(start)], start + 60 * 60_000).expired,
    ).toBe(true);
  });
  it("honors a server-reported expiry even without execution history", () => {
    expect(
      sessionAccess({ ...request, executionStatus: "EXECUTED" }, [], start)
        .expired,
    ).toBe(true);
  });
  it("does not invent a deadline for unlimited access", () => {
    expect(
      sessionAccess(
        { ...request, temporaryAccessDuration: null },
        [execution(start)],
        start + 100 * 60_000,
      ).label,
    ).toBe("No expiry");
  });
  it("explains pending and closed requests", () => {
    expect(
      sessionAccess(
        { ...request, reviewStatus: "AWAITING_APPROVAL" },
        [],
        start,
      ).label,
    ).toBe("Awaiting approval");
    expect(
      sessionAccess({ ...request, reviewStatus: "REJECTED" }, [], start).label,
    ).toBe("Request closed");
  });
});
