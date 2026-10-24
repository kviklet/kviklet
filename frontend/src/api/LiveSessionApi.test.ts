import { describe, it, expect } from "vitest";
import { responseMessage } from "./LiveSessionApi";

describe("LiveSessionApi", () => {
  it("keeps the stored messages on the event of a result message", () => {
    const parsed = responseMessage.parse({
      type: "result",
      sessionId: "session-id",
      results: [{ _type: "update", rowsUpdated: 0 }],
      messages: [{ level: "WARNING", message: "skipping it" }],
      event: {
        type: "EXECUTE",
        id: "event-id",
        createdAt: "2026-10-23T10:00:00Z",
        query: "ANALYZE booking;",
        results: [{ type: "UPDATE", rowsUpdated: 0 }],
        messages: [{ level: "WARNING", message: "skipping it" }],
        messageCount: 1,
      },
    });

    expect(parsed.type === "result" && parsed.event).toMatchObject({
      _type: "EXECUTE",
      messages: [{ level: "WARNING", message: "skipping it" }],
      messageCount: 1,
    });
  });
});
