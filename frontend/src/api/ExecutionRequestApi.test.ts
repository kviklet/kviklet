import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { downloadResults } from "./ExecutionRequestApi";
import baseUrl from "./base";

const fetchMock = vi.fn<typeof fetch>();
const clickMock = vi.fn();
const revokeObjectURLMock = vi.fn();

describe("downloadResults", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.stubGlobal("fetch", fetchMock);
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(
      clickMock,
    );
    vi.stubGlobal("URL", {
      createObjectURL: vi.fn(() => "blob:download"),
      revokeObjectURL: revokeObjectURLMock,
    });
    fetchMock.mockResolvedValue(
      new Response("value\n1\n", {
        headers: {
          "Content-Disposition": 'attachment; filename="results.csv"',
        },
      }),
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
    fetchMock.mockReset();
  });

  it("sends large SQL in a JSON body without adding it to the URL", async () => {
    const query = `SELECT 'café & + % ? # \\"' /* ${"x".repeat(32768)} */\n`;

    await downloadResults("request-id", query);

    expect(fetchMock).toHaveBeenCalledWith(
      `${baseUrl}/execution-requests/request-id/download`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "X-Kviklet-Request": "true",
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ query }),
      },
    );
    expect(clickMock).toHaveBeenCalledOnce();
    expect(revokeObjectURLMock).toHaveBeenCalledWith("blob:download");
  });

  it("omits SQL when downloading a stored single query", async () => {
    await downloadResults("request-id");

    expect(fetchMock).toHaveBeenCalledWith(
      `${baseUrl}/execution-requests/request-id/download`,
      expect.objectContaining({ method: "POST", body: "{}" }),
    );
  });

  it("reports API errors without producing a download", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({ message: "Query failed", error: "Bad Request" }),
        { status: 400 },
      ),
    );

    await expect(downloadResults("request-id", "invalid SQL")).rejects.toThrow(
      "Query failed",
    );
    expect(clickMock).not.toHaveBeenCalled();
  });
});
