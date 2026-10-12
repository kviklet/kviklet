import { act, fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import McpSettings from "./McpSettings";

let finishSave: () => void = () => {};
const updateConfig = vi.fn(
  () => new Promise<void>((resolve) => (finishSave = resolve)),
);

vi.mock("../../components/ConfigProvider", () => ({
  default: () => ({
    config: { licenseValid: true, mcpEnabled: true },
    updateConfig,
  }),
}));
vi.mock("../../hooks/permissions", () => ({
  useHasPermission: () => true,
}));

describe("McpSettings", () => {
  it("flips the toggle right away and ignores clicks until the save is done", async () => {
    render(<McpSettings />);
    const checkbox = screen.getByRole("checkbox");
    const toggle = checkbox.closest("label")!;

    fireEvent.click(toggle);
    expect(checkbox).not.toBeChecked();
    expect(checkbox).toBeDisabled();

    fireEvent.click(toggle);
    expect(updateConfig).toHaveBeenCalledTimes(1);
    expect(updateConfig).toHaveBeenCalledWith({ mcpEnabled: false });

    await act(() => {
      finishSave();
      return Promise.resolve();
    });
    // The mocked config never changes, like after a failed save: back to the stored value.
    expect(checkbox).toBeChecked();
    expect(checkbox).not.toBeDisabled();
  });
});
