import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import McpNudge from "./McpNudge";

const config = { licenseValid: true, mcpEnabled: true };

vi.mock("./ConfigProvider", () => ({
  default: () => ({ config }),
}));

const renderNudge = () =>
  render(
    <MemoryRouter>
      <McpNudge />
    </MemoryRouter>,
  );

describe("McpNudge", () => {
  beforeEach(() => {
    localStorage.clear();
    config.licenseValid = true;
    config.mcpEnabled = true;
  });

  it("shows the commands with this instance's MCP URL", () => {
    renderNudge();

    expect(
      screen.getByText(/^claude mcp add --transport http kviklet http.*\/mcp$/),
    ).toBeVisible();
    expect(
      screen.getByText(/^codex mcp add kviklet --url http.*\/mcp$/),
    ).toBeVisible();
  });

  it("stays closed after the X, also on the next visit", () => {
    renderNudge();

    fireEvent.click(screen.getByTestId("mcp-nudge-dismiss"));
    expect(screen.queryByTestId("mcp-nudge")).not.toBeInTheDocument();

    renderNudge();
    expect(screen.queryByTestId("mcp-nudge")).not.toBeInTheDocument();
  });

  it("is not shown without a license or while the MCP server is off", () => {
    config.mcpEnabled = false;
    renderNudge().unmount();
    expect(screen.queryByTestId("mcp-nudge")).not.toBeInTheDocument();

    config.mcpEnabled = true;
    config.licenseValid = false;
    renderNudge();
    expect(screen.queryByTestId("mcp-nudge")).not.toBeInTheDocument();
  });
});
