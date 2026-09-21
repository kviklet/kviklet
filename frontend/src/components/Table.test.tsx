import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect, vi } from "vitest";
import Table from "./Table";
import { SelectExecuteResponse } from "../api/ExecutionRequestApi";

const longValue = "x".repeat(41);

const data: SelectExecuteResponse = {
  _type: "select",
  columns: [
    { label: "id", typeName: "int4", typeClass: "java.lang.Integer" },
    { label: "payload", typeName: "text", typeClass: "java.lang.String" },
  ],
  data: [
    { id: "1", payload: "short" },
    { id: "2", payload: longValue },
    { id: "3", payload: "two\nlines" },
  ],
};

describe("Table", () => {
  it("only makes values that may be clipped clickable", () => {
    render(<Table data={data} />);
    const openers = screen.getAllByTestId("result-table-cell-open");
    expect(openers.map((button) => button.textContent)).toEqual([
      longValue,
      "two\nlines",
    ]);
    expect(screen.queryByTestId("cell-value-dialog")).toBeNull();
  });

  it("shows the full value in a dialog and copies it", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.assign(navigator, { clipboard: { writeText } });
    render(<Table data={data} />);

    userEvent.click(screen.getByRole("button", { name: longValue }));
    const dialog = screen.getByRole("dialog", { name: "Value of payload" });
    expect(within(dialog).getByText(longValue)).toBeInTheDocument();
    expect(dialog).toHaveTextContent("text");
    expect(dialog).toHaveTextContent("41 chars");

    userEvent.click(within(dialog).getByTestId("cell-value-copy"));
    expect(writeText).toHaveBeenCalledWith(longValue);
    expect(
      await within(dialog).findByRole("button", { name: "Copied" }),
    ).toBeInTheDocument();

    act(() => {
      userEvent.keyboard("{Escape}");
    });
    expect(screen.queryByRole("dialog")).toBeNull();
    expect(screen.getByRole("button", { name: longValue })).toHaveFocus();
  });

  it("closes with the close button and by clicking the backdrop", () => {
    render(<Table data={data} />);

    userEvent.click(screen.getByRole("button", { name: longValue }));
    userEvent.click(screen.getByTestId("cell-value-close"));
    expect(screen.queryByRole("dialog")).toBeNull();

    userEvent.click(screen.getByRole("button", { name: longValue }));
    userEvent.click(screen.getByTestId("cell-value-dialog"));
    expect(screen.queryByRole("dialog")).toBeNull();
  });
});
