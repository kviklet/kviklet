import { afterEach, describe, expect, it, vi } from "vitest";
import { z } from "zod";
import {
  fetchWithErrorHandling,
  isApiErrorResponse,
  parseSchemaOrError,
} from "./Errors";

const itemSchema = z.object({
  id: z.string(),
  createdAt: z.coerce.date(),
});

describe("parseSchemaOrError", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("returns the parsed output of the schema", () => {
    const parsed = parseSchemaOrError(itemSchema, {
      id: "a",
      createdAt: "2026-01-02T03:04:05Z",
    });

    expect(parsed).toEqual({
      id: "a",
      createdAt: new Date("2026-01-02T03:04:05Z"),
    });
  });

  it("passes a backend error message through", () => {
    const parsed = parseSchemaOrError(itemSchema, {
      message: "Connection not found",
    });

    expect(parsed).toEqual({ message: "Connection not found" });
    expect(isApiErrorResponse(parsed)).toBe(true);
  });

  it("turns a response matching neither schema into a generic error", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});

    const parsed = parseSchemaOrError(itemSchema, { id: 1 });

    expect(parsed).toEqual({ message: "Invalid server response." });
  });
});

describe("isApiErrorResponse", () => {
  it("only accepts objects with a string message", () => {
    expect(isApiErrorResponse({ message: "boom" })).toBe(true);
    expect(isApiErrorResponse({ message: 1 })).toBe(false);
    expect(isApiErrorResponse({ id: "a" })).toBe(false);
    expect(isApiErrorResponse(null)).toBe(false);
  });
});

describe("fetchWithErrorHandling", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("parses the JSON body with the given schema", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve(
          Response.json({ id: "a", createdAt: "2026-01-02T03:04:05Z" }),
        ),
      ),
    );

    const response = await fetchWithErrorHandling("/items/a", {}, itemSchema);

    expect(response).toEqual({
      id: "a",
      createdAt: new Date("2026-01-02T03:04:05Z"),
    });
  });

  it("reports a network failure as an error message", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new Error("Failed to fetch"))),
    );

    const response = await fetchWithErrorHandling("/items/a", {}, itemSchema);

    expect(response).toEqual({ message: "Failed to fetch" });
  });
});
