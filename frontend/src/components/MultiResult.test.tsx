import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import MultiResult from "./MultiResult";
import {
  ErrorResponse,
  ExecutionMessage,
  UpdateExecuteResponse,
} from "../api/ExecutionRequestApi";

const update: UpdateExecuteResponse = { _type: "update", rowsUpdated: 3 };

describe("MultiResult", () => {
  it("shows the messages the database sent below the result", () => {
    render(
      <MultiResult
        results={[update]}
        messages={[
          { level: "NOTICE", message: "checked 20 bookings" },
          {
            level: "WARNING",
            message: 'permission denied to analyze "booking", skipping it',
          },
        ]}
      />,
    );
    const messages = screen.getByTestId("execution-messages");
    expect(messages).toHaveTextContent("Messages · 2");
    expect(messages).toHaveTextContent("NOTICEchecked 20 bookings");
    expect(messages).toHaveTextContent(
      'WARNINGpermission denied to analyze "booking", skipping it',
    );
    const body = screen.getByText("Statement executed");
    expect(
      body.compareDocumentPosition(messages) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("shows no messages section without messages", () => {
    render(<MultiResult results={[update]} messages={[]} />);
    expect(screen.queryByTestId("execution-messages")).toBeNull();
  });

  it("describes a statement without rows by the rows it affected", () => {
    render(<MultiResult results={[update]} messages={[]} />);
    expect(screen.getByText("Statement executed")).toBeInTheDocument();
    expect(screen.getByText("3 rows affected")).toBeInTheDocument();
    expect(screen.getByText("Statement · 3 rows affected")).toBeInTheDocument();
  });

  it("shows a failed statement with its SQLSTATE", () => {
    const error: ErrorResponse = {
      _type: "error",
      errorCode: 0,
      message: 'ERROR: relation "bookings" does not exist',
      sqlState: "42P01",
    };
    render(<MultiResult results={[error]} messages={[]} />);
    expect(screen.getByText("Error · SQLSTATE 42P01")).toBeInTheDocument();
    expect(screen.getByText("Statement failed")).toBeInTheDocument();
    expect(
      screen.getByText('ERROR: relation "bookings" does not exist'),
    ).toBeInTheDocument();
  });

  it("shows the messages of a run that returned no results", () => {
    render(
      <MultiResult
        results={[]}
        messages={[{ level: "NOTICE", message: "printed from the batch" }]}
      />,
    );
    expect(screen.getByText("No results")).toBeInTheDocument();
    expect(screen.getByTestId("execution-messages")).toHaveTextContent(
      "NOTICEprinted from the batch",
    );
  });

  it("collapses long message lists until asked", () => {
    const many: ExecutionMessage[] = Array.from({ length: 7 }, (_, i) => ({
      level: "NOTICE",
      message: `processed row ${i + 1}`,
    }));
    render(<MultiResult results={[update]} messages={many} />);
    const messages = screen.getByTestId("execution-messages");
    expect(within(messages).getAllByRole("listitem")).toHaveLength(5);

    userEvent.click(
      within(messages).getByRole("button", { name: "Show all 7 messages" }),
    );
    expect(within(messages).getAllByRole("listitem")).toHaveLength(7);
  });
});
