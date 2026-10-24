import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, it, expect } from "vitest";
import ExecuteEventResults from "./ExecuteEventResults";
import { Execute, ExecuteEvent } from "../../../api/ExecutionRequestApi";

const event = (overrides: Partial<Execute>): Execute => ({
  _type: "EXECUTE",
  type: "EXECUTE",
  id: "event-id",
  createdAt: new Date("2026-10-23T10:00:00Z"),
  query: "ANALYZE booking;",
  results: [{ type: "UPDATE", rowsUpdated: 0 }],
  ...overrides,
});

describe("ExecuteEventResults", () => {
  it("counts the messages on the collapsed results line", () => {
    render(
      <ExecuteEventResults
        event={event({
          messages: [
            { level: "NOTICE", message: "checked 20 bookings" },
            { level: "WARNING", message: "skipping it" },
          ],
          messageCount: 2,
        })}
      />,
    );
    expect(
      screen.getByTestId("execute-event-message-summary"),
    ).toHaveTextContent("1 warning · 1 notice");
  });

  it("shows the stored results and messages when opened", () => {
    render(
      <ExecuteEventResults
        event={event({
          results: [
            { type: "UPDATE", rowsUpdated: 0 },
            {
              type: "ERROR",
              errorCode: 0,
              message: "duplicate key",
              sqlState: "23505",
            },
          ],
          messages: [{ level: "NOTICE", message: "copying 12 bookings" }],
          messageCount: 1,
        })}
      />,
    );
    userEvent.click(screen.getByRole("button", { name: /Results/ }));

    expect(
      screen.getByText("Statement executed · 0 rows affected"),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Statement failed · SQLSTATE 23505"),
    ).toBeInTheDocument();
    expect(screen.getByTestId("execution-messages")).toHaveTextContent(
      "NOTICEcopying 12 bookings",
    );
  });

  it("says how many messages were not stored", () => {
    const stored = Array.from({ length: 100 }, (_, i) => ({
      level: "NOTICE" as const,
      message: `row ${i + 1}`,
    }));
    render(
      <ExecuteEventResults
        event={event({ messages: stored, messageCount: 1284 })}
      />,
    );
    expect(
      screen.getByTestId("execute-event-message-summary"),
    ).toHaveTextContent("1,284 messages");

    userEvent.click(screen.getByRole("button", { name: /Results/ }));
    userEvent.click(
      screen.getByRole("button", { name: "Show all 100 messages" }),
    );
    expect(screen.getByText("1,184 more not stored")).toBeInTheDocument();
  });

  it("shows the messages of a run that returned no results", () => {
    render(
      <ExecuteEventResults
        event={event({
          results: [],
          messages: [{ level: "NOTICE", message: "printed from the batch" }],
          messageCount: 1,
        })}
      />,
    );
    userEvent.click(screen.getByRole("button", { name: /Results/ }));
    expect(screen.getByTestId("execution-messages")).toHaveTextContent(
      "NOTICEprinted from the batch",
    );
  });

  it("keeps NULL cells in stored rows apart from the text 'null'", () => {
    // Parsed from the API's JSON so the schema has to keep the null
    const parsed = ExecuteEvent.parse({
      _type: "EXECUTE",
      type: "EXECUTE",
      id: "event-id",
      createdAt: "2026-10-23T10:00:00Z",
      query: "SELECT 1 AS id, NULL AS note, 'null' AS text",
      results: [
        {
          type: "QUERY",
          columnCount: 3,
          rowCount: 1,
          columns: [
            { label: "id", typeName: "int4", typeClass: "java.lang.Integer" },
            { label: "note", typeName: "text", typeClass: "java.lang.String" },
            { label: "text", typeName: "text", typeClass: "java.lang.String" },
          ],
          storedRows: [{ id: "1", note: null, text: "null" }],
          storedRowCount: 1,
        },
      ],
    });
    render(<ExecuteEventResults event={parsed} />);
    userEvent.click(screen.getByRole("button", { name: /Results/ }));

    expect(screen.getByText("NULL")).toHaveClass("italic");
    expect(screen.getByText("null")).not.toHaveClass("italic");
  });

  it("shows no count and no messages section without messages", () => {
    render(<ExecuteEventResults event={event({})} />);
    expect(screen.queryByTestId("execute-event-message-summary")).toBeNull();

    userEvent.click(screen.getByRole("button", { name: /Results/ }));
    expect(screen.queryByTestId("execution-messages")).toBeNull();
  });
});
