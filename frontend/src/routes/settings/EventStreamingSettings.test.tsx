import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EventStreamingSettings from "./EventStreamingSettings";
import {
  getEventStreaming,
  putEventStreaming,
} from "../../api/EventStreamingApi";
import { UserStatusContext } from "../../components/UserStatusProvider";
import { StatusResponse } from "../../api/StatusApi";
import { Permission } from "../../api/Permissions";
const state = vi.hoisted(() => ({ licenseValid: true }));
vi.mock("../../api/EventStreamingApi", async (original) => {
  const actual = await original<typeof import("../../api/EventStreamingApi")>();
  return { ...actual, getEventStreaming: vi.fn(), putEventStreaming: vi.fn() };
});
vi.mock("../../components/ConfigProvider", () => ({
  default: () => ({ config: { licenseValid: state.licenseValid } }),
}));
const response = {
  settings: {
    enabled: false,
    directory: "/var/log/kviklet/events",
    maxFileSizeMiB: 100,
    retentionDays: 180,
    maxArchiveSizeMiB: 100,
  },
  status: {
    state: "disabled" as const,
    lastWriteAt: null,
    lastError: null,
    detectedFailures: 0,
  },
};
const page = (
  permissions: Permission[] = ["configuration:get", "configuration:edit"],
) =>
  render(
    <MemoryRouter>
      <UserStatusContext.Provider
        value={{
          userStatus: { id: "admin" } as unknown as StatusResponse,
          refreshState: async () => {},
          hasPermission: (permission) => permissions.includes(permission),
          logout: async () => {},
          loggedOut: false,
        }}
      >
        <EventStreamingSettings />
      </UserStatusContext.Provider>
    </MemoryRouter>,
  );
beforeEach(() => {
  vi.clearAllMocks();
  state.licenseValid = true;
  vi.mocked(getEventStreaming).mockResolvedValue(response);
  vi.mocked(putEventStreaming).mockResolvedValue(response);
});
describe("Event streaming settings", () => {
  it("keeps unlicensed enable locked and shows the license link", async () => {
    state.licenseValid = false;
    page();
    expect(await screen.findByRole("checkbox")).toBeDisabled();
    expect(
      screen.getByRole("link", { name: "Manage license" }),
    ).toHaveAttribute("href", "/settings/license");
  });
  it("allows disabling an expired stream", async () => {
    state.licenseValid = false;
    vi.mocked(getEventStreaming).mockResolvedValue({
      ...response,
      settings: { ...response.settings, enabled: true },
    });
    page();
    expect(await screen.findByRole("checkbox")).toBeEnabled();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByRole("status")).toHaveTextContent(
      "Settings saved.",
    );
    expect(putEventStreaming).toHaveBeenCalledWith(response.settings);
  });
  it("makes settings read only without edit permission", async () => {
    page(["configuration:get"]);
    expect(await screen.findByRole("checkbox")).toBeDisabled();
    expect(
      screen.getByLabelText("Output directory (absolute path)"),
    ).toBeDisabled();
    expect(
      screen.queryByRole("button", { name: "Save" }),
    ).not.toBeInTheDocument();
  });
  it("shows backend failures without a success notification", async () => {
    vi.mocked(putEventStreaming).mockResolvedValue({
      message: "Cannot open event directory",
    });
    page();
    await screen.findByRole("checkbox");
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cannot open event directory",
    );
    expect(screen.queryByText("Settings saved.")).not.toBeInTheDocument();
  });
  it("validates archive budget and explains directory migration", async () => {
    page();
    await screen.findByRole("checkbox");
    fireEvent.change(
      screen.getByLabelText("Output directory (absolute path)"),
      { target: { value: "/another/directory" } },
    );
    expect(screen.getByText(/leaves existing files/)).toBeVisible();
    fireEvent.change(screen.getByLabelText("Maximum archive size (MiB)"), {
      target: { value: "1" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    expect(
      await screen.findByText(/must cover at least one rotated file/),
    ).toBeVisible();
    expect(putEventStreaming).not.toHaveBeenCalled();
  });
});
