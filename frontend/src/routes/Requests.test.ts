import { renderHook, act, waitFor } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach, MockedFunction } from "vitest";
import { useRequests } from "./Requests";
import {
  ExecutionRequestResponse,
  getRequestsPaginated,
} from "../api/ExecutionRequestApi";
import { RequestListFilters, emptyFilters } from "./RequestFilters";

vi.mock("../api/ExecutionRequestApi");

vi.mock("../hooks/useNotification", () => ({
  default: () => ({
    addNotification: vi.fn(),
  }),
}));

const mockGetRequestsPaginated = getRequestsPaginated as MockedFunction<
  typeof getRequestsPaginated
>;

type ListResult = Awaited<ReturnType<typeof getRequestsPaginated>>;

const makeRequest = (id: string, title: string): ExecutionRequestResponse =>
  ({
    id,
    title,
    description: "",
    type: "SingleExecution",
    reviewStatus: "AWAITING_APPROVAL",
    executionStatus: "EXECUTABLE",
    author: { id: "author-1", email: "author@example.com", fullName: "Author" },
    connection: { id: "conn-1", displayName: "Test Connection" },
    createdAt: "2026-08-01T12:00:00",
    _type: "DATASOURCE",
  }) as unknown as ExecutionRequestResponse;

const listResponse = (
  requests: ExecutionRequestResponse[],
  hasMore = false,
  cursor: Date | null = null,
): ListResult => ({ requests, hasMore, cursor }) as ListResult;

describe("useRequests hook", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetRequestsPaginated.mockResolvedValue(listResponse([]));
  });

  describe("stale response guard", () => {
    it("ignores an earlier fetch that resolves after a newer one", async () => {
      let resolveFirst!: (value: ListResult) => void;
      let resolveSecond!: (value: ListResult) => void;
      mockGetRequestsPaginated
        .mockReturnValueOnce(
          new Promise<ListResult>((resolve) => {
            resolveFirst = resolve;
          }),
        )
        .mockReturnValueOnce(
          new Promise<ListResult>((resolve) => {
            resolveSecond = resolve;
          }),
        );

      const { result, rerender } = renderHook(
        ({ filters }: { filters: RequestListFilters }) =>
          useRequests(filters, ""),
        { initialProps: { filters: emptyFilters } },
      );

      // A filter change fires a second fetch while the first is in flight
      rerender({ filters: { ...emptyFilters, authorId: "author-1" } });
      await waitFor(() =>
        expect(mockGetRequestsPaginated).toHaveBeenCalledTimes(2),
      );

      // The newer (filtered) response arrives first
      await act(async () => {
        resolveSecond(listResponse([makeRequest("2", "Filtered result")]));
        await Promise.resolve();
      });
      // The stale (unfiltered) response arrives last and must be dropped
      await act(async () => {
        resolveFirst(listResponse([makeRequest("1", "Stale result")]));
        await Promise.resolve();
      });

      expect(result.current.requests).toHaveLength(1);
      expect(result.current.requests[0].title).toBe("Filtered result");
    });
  });

  describe("filter to request param mapping", () => {
    const lastCallParams = () =>
      mockGetRequestsPaginated.mock.calls[
        mockGetRequestsPaginated.mock.calls.length - 1
      ][0];

    it("sends no filter params for empty filters", async () => {
      renderHook(() => useRequests(emptyFilters, ""));
      await waitFor(() => expect(mockGetRequestsPaginated).toHaveBeenCalled());

      expect(lastCallParams()).toEqual({
        reviewStatuses: undefined,
        executionStatuses: undefined,
        connectionIds: undefined,
        authorId: undefined,
        createdAfter: undefined,
        createdBefore: undefined,
        after: undefined,
        limit: 20,
      });
    });

    it("maps onlyPending to the pending status sets", async () => {
      const filters = { ...emptyFilters, onlyPending: true };
      renderHook(() => useRequests(filters, ""));
      await waitFor(() => expect(mockGetRequestsPaginated).toHaveBeenCalled());

      expect(lastCallParams()).toMatchObject({
        reviewStatuses: ["AWAITING_APPROVAL"],
        executionStatuses: ["EXECUTABLE", "ACTIVE"],
      });
    });

    it("passes selected connection ids and author id", async () => {
      const filters = {
        ...emptyFilters,
        connectionIds: ["conn-a", "conn-b"],
        authorId: "user-1",
      };
      renderHook(() => useRequests(filters, ""));
      await waitFor(() => expect(mockGetRequestsPaginated).toHaveBeenCalled());

      expect(lastCallParams()).toMatchObject({
        connectionIds: ["conn-a", "conn-b"],
        authorId: "user-1",
      });
    });

    it("expands the date range to full local days", async () => {
      const filters = {
        ...emptyFilters,
        createdFrom: "2026-08-01",
        createdTo: "2026-08-19",
      };
      renderHook(() => useRequests(filters, ""));
      await waitFor(() => expect(mockGetRequestsPaginated).toHaveBeenCalled());

      expect(lastCallParams()).toMatchObject({
        createdAfter: new Date("2026-08-01T00:00:00"),
        createdBefore: new Date("2026-08-19T23:59:59.999"),
      });
    });

    it("resets the list and cursor when filters change", async () => {
      const cursor = new Date("2026-08-10T00:00:00");
      mockGetRequestsPaginated
        .mockResolvedValueOnce(
          listResponse([makeRequest("1", "First page")], true, cursor),
        )
        .mockResolvedValueOnce(listResponse([makeRequest("2", "Filtered")]));

      const { result, rerender } = renderHook(
        ({ filters }: { filters: RequestListFilters }) =>
          useRequests(filters, ""),
        { initialProps: { filters: emptyFilters } },
      );
      await waitFor(() => expect(result.current.requests).toHaveLength(1));

      rerender({ filters: { ...emptyFilters, authorId: "user-1" } });
      await waitFor(() =>
        expect(result.current.requests[0]?.title).toBe("Filtered"),
      );

      // Replaced, not appended, and fetched from the start again
      expect(result.current.requests).toHaveLength(1);
      expect(lastCallParams()).toMatchObject({ after: undefined });
    });

    it("passes the cursor when loading more and appends the results", async () => {
      const cursor = new Date("2026-08-10T00:00:00");
      mockGetRequestsPaginated
        .mockResolvedValueOnce(
          listResponse([makeRequest("1", "First page")], true, cursor),
        )
        .mockResolvedValueOnce(
          listResponse([makeRequest("2", "Second page")], false, null),
        );

      const { result } = renderHook(() => useRequests(emptyFilters, ""));
      await waitFor(() => expect(result.current.requests).toHaveLength(1));
      expect(result.current.hasMore).toBe(true);

      act(() => {
        result.current.loadMore();
      });
      await waitFor(() => expect(result.current.requests).toHaveLength(2));

      expect(lastCallParams()).toMatchObject({ after: cursor });
      expect(result.current.requests.map((r) => r.title)).toEqual([
        "First page",
        "Second page",
      ]);
      expect(result.current.hasMore).toBe(false);
    });
  });
});
