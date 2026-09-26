import { render, screen } from "@testing-library/react";
import { vi, describe, it, expect, beforeEach, MockedFunction } from "vitest";
import LicenseSettings from "./LicenseSettings";
import { fetchUsers } from "../../api/UserApi";
import { UserStatusContext } from "../../components/UserStatusProvider";
import { StatusResponse } from "../../api/StatusApi";
import { Permission } from "../../api/Permissions";

vi.mock("../../api/UserApi");
vi.mock("../../components/ConfigProvider", () => ({
  default: () => ({
    config: {
      ldapEnabled: false,
      samlEnabled: false,
      licenseValid: false,
      validUntil: null,
      proxyEnabled: false,
      version: "test",
      buildDate: "test",
      gitCommit: "test",
    },
    loading: false,
    refreshConfig: async () => {},
    updateConfig: async () => {},
  }),
}));

const mockFetchUsers = fetchUsers as MockedFunction<typeof fetchUsers>;

const renderPage = (permissions: Permission[]) =>
  render(
    <UserStatusContext.Provider
      value={{
        userStatus: { id: "me" } as unknown as StatusResponse,
        refreshState: async () => {},
        hasPermission: (permission) => permissions.includes(permission),
        logout: async () => {},
        loggedOut: false,
      }}
    >
      <LicenseSettings />
    </UserStatusContext.Provider>,
  );

describe("LicenseSettings", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockFetchUsers.mockResolvedValue({ users: [] });
  });

  it("shows the upload dropzone to admins", async () => {
    renderPage(["configuration:get", "configuration:edit"]);

    expect(await screen.findByText("Click to upload")).toBeVisible();
    expect(
      screen.queryByText(
        /You need to be an administrator to upload a license file/,
      ),
    ).not.toBeInTheDocument();
  });

  it("shows non-admins a disabled dropzone with an admin notice", async () => {
    renderPage(["configuration:get"]);

    expect(
      await screen.findByText(
        /You need to be an administrator to upload a license file/,
      ),
    ).toBeVisible();
    expect(screen.queryByText("Click to upload")).not.toBeInTheDocument();
    expect(screen.getByLabelText("License file")).toBeDisabled();
  });
});
