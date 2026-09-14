import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { vi, describe, it, expect, beforeEach, MockedFunction } from "vitest";
import UserSettings from "./UserSettings";
import { fetchUsers, setUserActive, UserResponse } from "../../api/UserApi";
import { getRoles } from "../../api/RoleApi";
import { UserStatusContext } from "../../components/UserStatusProvider";
import { StatusResponse } from "../../api/StatusApi";

vi.mock("../../api/UserApi");
vi.mock("../../api/RoleApi");
// The role combobox is a Headless UI popover with an `anchor`, which does not terminate in
// jsdom; it is not what these tests are about.
vi.mock("./RoleComboBox", () => ({
  default: () => <div data-testid="role-combobox" />,
}));

const mockFetchUsers = fetchUsers as MockedFunction<typeof fetchUsers>;
const mockSetUserActive = setUserActive as MockedFunction<typeof setUserActive>;
const mockGetRoles = getRoles as MockedFunction<typeof getRoles>;

const me = {
  id: "me",
  email: "me@example.com",
  fullName: "Current Admin",
} as unknown as StatusResponse;

const user = (id: string, fullName: string, active: boolean): UserResponse => ({
  id,
  email: `${id}@example.com`,
  fullName,
  active,
  roles: [],
});

const renderPage = () =>
  render(
    <MemoryRouter>
      <UserStatusContext.Provider
        value={{
          userStatus: me,
          refreshState: async () => {},
          hasPermission: () => true,
          logout: async () => {},
          loggedOut: false,
        }}
      >
        <UserSettings />
      </UserStatusContext.Provider>
    </MemoryRouter>,
  );

describe("UserSettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetRoles.mockResolvedValue({ roles: [] });
    mockFetchUsers.mockResolvedValue({
      users: [
        user("me", "Current Admin", true),
        user("alice", "Alice Active", true),
        user("bob", "Bob Inactive", false),
      ],
    });
  });

  it("lists deactivated users last, muted and badged", async () => {
    mockFetchUsers.mockResolvedValue({
      users: [
        user("bob", "Bob Inactive", false),
        user("me", "Current Admin", true),
        user("alice", "Alice Active", true),
      ],
    });
    renderPage();

    const bobRow = await screen.findByTestId("user-bob@example.com");
    expect(bobRow).toHaveAttribute("data-deactivated", "true");
    expect(within(bobRow).getByTestId("deactivated-badge")).toBeVisible();
    expect(within(bobRow).getByTestId("reactivate-user")).toBeVisible();

    const rows = screen
      .getAllByTestId(/^user-/)
      .map((row) => row.getAttribute("data-testid"));
    expect(rows).toEqual([
      "user-me@example.com",
      "user-alice@example.com",
      "user-bob@example.com",
    ]);
    expect(
      within(screen.getByTestId("user-alice@example.com")).queryByTestId(
        "deactivated-badge",
      ),
    ).toBeNull();
  });

  it("deactivates another user after confirmation, never the current user", async () => {
    mockSetUserActive.mockResolvedValue(user("alice", "Alice Active", false));
    renderPage();

    const myRow = await screen.findByTestId("user-me@example.com");
    expect(within(myRow).getByTestId("deactivate-user")).toBeDisabled();

    const aliceRow = screen.getByTestId("user-alice@example.com");
    userEvent.click(within(aliceRow).getByTestId("deactivate-user"));
    expect(mockSetUserActive).not.toHaveBeenCalled();

    userEvent.click(screen.getByRole("button", { name: "Confirm" }));

    await waitFor(() =>
      expect(mockSetUserActive).toHaveBeenCalledWith("alice", false),
    );
    // The row stays in the list, now marked deactivated and sorted below the active ones.
    await waitFor(() =>
      expect(screen.getByTestId("user-alice@example.com")).toHaveAttribute(
        "data-deactivated",
        "true",
      ),
    );
    const rows = screen
      .getAllByTestId(/^user-/)
      .map((row) => row.getAttribute("data-testid"));
    expect(rows).toEqual([
      "user-me@example.com",
      "user-alice@example.com",
      "user-bob@example.com",
    ]);
    expect(screen.getByText("Alice Active has been deactivated")).toBeVisible();
  });

  it("reactivates a deactivated user", async () => {
    mockSetUserActive.mockResolvedValue(user("bob", "Bob Inactive", true));
    renderPage();

    const bobRow = await screen.findByTestId("user-bob@example.com");
    userEvent.click(within(bobRow).getByTestId("reactivate-user"));

    await waitFor(() =>
      expect(mockSetUserActive).toHaveBeenCalledWith("bob", true),
    );
    await waitFor(() =>
      expect(screen.getByTestId("user-bob@example.com")).not.toHaveAttribute(
        "data-deactivated",
      ),
    );
    expect(
      within(screen.getByTestId("user-bob@example.com")).getByTestId(
        "deactivate-user",
      ),
    ).toBeEnabled();
  });

  it("surfaces a rejected reactivation", async () => {
    mockSetUserActive.mockResolvedValue({
      message: "License does not allow more active users",
    });
    renderPage();

    const bobRow = await screen.findByTestId("user-bob@example.com");
    userEvent.click(within(bobRow).getByTestId("reactivate-user"));

    expect(
      await screen.findByText("License does not allow more active users"),
    ).toBeVisible();
    expect(screen.getByTestId("user-bob@example.com")).toHaveAttribute(
      "data-deactivated",
      "true",
    );
  });
});
