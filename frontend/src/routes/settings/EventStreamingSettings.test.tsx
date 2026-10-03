import { render, screen, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import EventStreamingSettings from "./EventStreamingSettings";
import {
  getEventStreaming,
  EventStreamingSettingsSchema,
} from "../../api/EventStreamingApi";
const state = vi.hoisted(() => ({ licenseValid: true, gitCommit: "unknown" }));
vi.mock("../../api/EventStreamingApi", async (original) => {
  const actual = await original<typeof import("../../api/EventStreamingApi")>();
  return { ...actual, getEventStreaming: vi.fn() };
});
vi.mock("../../components/ConfigProvider", () => ({
  default: () => ({ config: state }),
}));
const response = {
  settings: {
    enabled: false,
    directory: "/var/log/kviklet/events",
    maxFileSizeMiB: 10,
    retentionDays: 180,
    maxArchiveSizeMiB: 100,
    loggingLevel: "FULL" as const,
  },
  status: {
    state: "disabled" as const,
    lastWriteAt: null,
    lastError: null,
    detectedFailures: 0,
  },
};
const page = () =>
  render(
    <MemoryRouter>
      <EventStreamingSettings />
    </MemoryRouter>,
  );
beforeEach(() => {
  vi.clearAllMocks();
  state.licenseValid = true;
  state.gitCommit = "unknown";
  vi.mocked(getEventStreaming).mockResolvedValue(response);
});
describe("Event streaming deployment status", () => {
  it("shows all effective settings with no editing controls", async () => {
    page();
    expect(await screen.findByText("180 days")).toBeVisible();
    for (const text of [
      "Disabled",
      "/var/log/kviklet/events",
      "10 MiB",
      "100 MiB",
      "Level 3 — All events, with query text",
    ]) {
      expect(screen.getByText(text)).toBeVisible();
    }
    expect(
      screen.getByText(/Managed by deployment configuration/),
    ).toHaveTextContent("Changes require an application restart.");
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Save" }),
    ).not.toBeInTheDocument();
  });
  it("distinguishes enabled configuration from missing license status", async () => {
    state.licenseValid = false;
    vi.mocked(getEventStreaming).mockResolvedValue({
      ...response,
      settings: { ...response.settings, enabled: true },
      status: { ...response.status, state: "license_expired" },
    });
    page();
    expect(await screen.findByText("Enabled")).toBeVisible();
    expect(screen.getByText("license expired")).toBeVisible();
    expect(
      screen.getByRole("link", { name: "Manage license" }),
    ).toHaveAttribute("href", "/settings/license");
  });
  it.each(["SECURITY_ONLY", "WITHOUT_QUERY_TEXT", "FULL"] as const)(
    "explains configured level %s",
    async (loggingLevel) => {
      vi.mocked(getEventStreaming).mockResolvedValue({
        ...response,
        settings: { ...response.settings, loggingLevel },
      });
      page();
      await screen.findByText("180 days");
      const description =
        loggingLevel === "SECURITY_ONLY"
          ? /Routine request, review/
          : loggingLevel === "WITHOUT_QUERY_TEXT"
          ? /SQL statements and Kubernetes commands are omitted/
          : /These text fields may contain sensitive information/;
      expect(screen.getByText(description)).toBeVisible();
    },
  );
  it.each(["unknown", "0123456789abcdef0123456789abcdef01234567"])(
    "links setup instructions for build %s",
    async (commit) => {
      state.gitCommit = commit;
      page();
      await screen.findByText("180 days");
      expect(
        screen.getByRole("link", { name: "View setup instructions" }),
      ).toHaveAttribute(
        "href",
        `https://github.com/kviklet/kviklet/blob/${
          commit === "unknown" ? "main" : commit
        }/docs/event-streaming/README.md`,
      );
    },
  );
  it("shows degraded health and refreshes without saving", async () => {
    vi.mocked(getEventStreaming).mockResolvedValueOnce({
      ...response,
      status: {
        ...response.status,
        state: "degraded",
        lastError: "Cannot open event directory",
        detectedFailures: 2,
      },
    });
    page();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Cannot open event directory",
    );
    expect(
      screen.getByText(/Detected failures since restart: 2/),
    ).toBeVisible();
    fireEvent.click(screen.getByRole("button", { name: "Refresh status" }));
    expect(await screen.findByText("disabled")).toBeVisible();
    expect(getEventStreaming).toHaveBeenCalledTimes(2);
  });
  it("shows read permission and backend errors", async () => {
    vi.mocked(getEventStreaming).mockResolvedValue({
      message: "Permission denied",
    });
    page();
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Permission denied",
    );
    expect(screen.queryByText("180 days")).not.toBeInTheDocument();
  });
  it("rejects unknown logging levels in API responses", () => {
    expect(
      EventStreamingSettingsSchema.safeParse({
        ...response.settings,
        loggingLevel: "DEBUG",
      }).success,
    ).toBe(false);
  });
});
