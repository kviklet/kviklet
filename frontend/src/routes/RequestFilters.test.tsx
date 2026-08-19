import { useState } from "react";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { vi, describe, it, expect, beforeEach, MockedFunction } from "vitest";
import {
  RequestFilterBar,
  RequestListFilters,
  emptyFilters,
} from "./RequestFilters";
import { getConnections, ConnectionResponse } from "../api/DatasourceApi";
import { fetchUsers } from "../api/UserApi";
import { UserStatusContext } from "../components/UserStatusProvider";
import { StatusResponse } from "../api/StatusApi";

vi.mock("../api/DatasourceApi");
vi.mock("../api/UserApi");

const mockGetConnections = getConnections as MockedFunction<
  typeof getConnections
>;
const mockFetchUsers = fetchUsers as MockedFunction<typeof fetchUsers>;

const currentUser = {
  id: "me",
  email: "me@example.com",
  fullName: "Current User",
} as unknown as StatusResponse;

const makeConnection = (id: string, displayName: string) =>
  ({ id, displayName }) as unknown as ConnectionResponse;

const usersResponse = {
  users: [
    { id: "me", email: "me@example.com", fullName: "Current User", roles: [] },
    { id: "u2", email: "other@example.com", fullName: "Other User", roles: [] },
  ],
};

let lastFilters: RequestListFilters = emptyFilters;

function Harness() {
  const [filters, setFilters] = useState<RequestListFilters>(emptyFilters);
  lastFilters = filters;
  return <RequestFilterBar filters={filters} onChange={setFilters} />;
}

const renderFilterBar = () =>
  render(
    <UserStatusContext.Provider
      value={{
        userStatus: currentUser,
        refreshState: async () => {},
        hasPermission: () => true,
      }}
    >
      <Harness />
    </UserStatusContext.Provider>,
  );

describe("RequestFilterBar", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    lastFilters = emptyFilters;
    mockGetConnections.mockResolvedValue([
      makeConnection("conn-a", "Prod Postgres"),
      makeConnection("conn-b", "Staging MySQL"),
    ]);
    mockFetchUsers.mockResolvedValue(usersResponse);
  });

  describe("permission degradation", () => {
    it("still offers 'Your requests' when the user list is not accessible", async () => {
      mockFetchUsers.mockResolvedValue({ message: "Forbidden" });
      renderFilterBar();

      userEvent.click(await screen.findByTestId("filter-author"));

      expect(await screen.findByTestId("filter-author-mine")).toBeVisible();
      expect(screen.queryByText("Other User")).not.toBeInTheDocument();
    });

    it("hides the connection filter when connections are not accessible", async () => {
      mockGetConnections.mockResolvedValue({ message: "Forbidden" });
      renderFilterBar();

      // The author filter loading proves the fetches settled
      await screen.findByTestId("filter-author");
      await waitFor(() =>
        expect(
          screen.queryByTestId("filter-connection"),
        ).not.toBeInTheDocument(),
      );
      expect(screen.getByTestId("filter-created")).toBeInTheDocument();
      expect(screen.getByTestId("filter-pending")).toBeInTheDocument();
    });
  });

  describe("selection semantics", () => {
    it("toggles connections in and out of the multiselect", async () => {
      renderFilterBar();

      userEvent.click(await screen.findByTestId("filter-connection"));
      userEvent.click(
        await screen.findByTestId("filter-connection-Prod Postgres"),
      );
      expect(lastFilters.connectionIds).toEqual(["conn-a"]);
      expect(screen.getByTestId("filter-connection")).toHaveTextContent(
        "Prod Postgres",
      );

      userEvent.click(screen.getByTestId("filter-connection-Staging MySQL"));
      expect(lastFilters.connectionIds).toEqual(["conn-a", "conn-b"]);
      expect(screen.getByTestId("filter-connection")).toHaveTextContent(
        "2 connections",
      );

      userEvent.click(screen.getByTestId("filter-connection-Prod Postgres"));
      expect(lastFilters.connectionIds).toEqual(["conn-b"]);
      expect(screen.getByTestId("filter-connection")).toHaveTextContent(
        "Staging MySQL",
      );
    });

    it("selects a single author exclusively", async () => {
      renderFilterBar();

      userEvent.click(await screen.findByTestId("filter-author"));
      userEvent.click(await screen.findByTestId("filter-author-mine"));
      expect(lastFilters.authorId).toBe("me");
      expect(screen.getByTestId("filter-author")).toHaveTextContent(
        "Your requests",
      );

      userEvent.click(screen.getByTestId("filter-author"));
      userEvent.click(await screen.findByText("Other User"));
      expect(lastFilters.authorId).toBe("u2");
      expect(screen.getByTestId("filter-author")).toHaveTextContent(
        "Other User",
      );
    });

    it("clears a single filter from its pill", async () => {
      renderFilterBar();

      userEvent.click(await screen.findByTestId("filter-connection"));
      userEvent.click(
        await screen.findByTestId("filter-connection-Prod Postgres"),
      );
      expect(lastFilters.connectionIds).toEqual(["conn-a"]);

      userEvent.click(
        screen.getByRole("button", { name: /Clear Prod Postgres filter/ }),
      );
      expect(lastFilters.connectionIds).toEqual([]);
    });

    it("toggles the pending filter", async () => {
      renderFilterBar();

      const pendingPill = await screen.findByTestId("filter-pending");
      userEvent.click(pendingPill);
      expect(lastFilters.onlyPending).toBe(true);

      userEvent.click(pendingPill);
      expect(lastFilters.onlyPending).toBe(false);
    });

    it("clear filters resets everything including pending", async () => {
      renderFilterBar();

      userEvent.click(await screen.findByTestId("filter-connection"));
      userEvent.click(
        await screen.findByTestId("filter-connection-Prod Postgres"),
      );
      userEvent.click(document.body);
      userEvent.click(screen.getByTestId("filter-author"));
      userEvent.click(await screen.findByTestId("filter-author-mine"));
      userEvent.click(screen.getByTestId("filter-pending"));
      expect(lastFilters).toMatchObject({
        connectionIds: ["conn-a"],
        authorId: "me",
        onlyPending: true,
      });

      userEvent.click(screen.getByTestId("filter-clear-all"));
      expect(lastFilters).toEqual(emptyFilters);
    });
  });
});
