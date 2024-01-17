import { describe, it, expect, vi, Mock } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import AuditLog from "./Auditlog";
import * as ExecutionsApi from "../api/ExecutionsApi";
import { MemoryRouter } from "react-router-dom";

// Mock data conforming to ExecutionLogResponseSchema
const mockExecutions = {
  executions: [
    {
      requestId: "req123",
      name: "Query1",
      statement: "SELECT * FROM users",
      connectionId: "conn456",
      executionTime: new Date("2024-01-01T12:00:00.000Z"),
    },
    {
      requestId: "req789",
      name: "Query2",
      statement: 'UPDATE users SET name = "John Doe" WHERE id = 1',
      connectionId: "conn101",
      executionTime: new Date("2024-01-02T12:00:00.000Z"),
    },
    // Add more items as needed
  ],
};

// Replace the real ExecutionsApi module with the mock module
vi.mock("../api/ExecutionsApi", () => ({
  getExecutions: vi.fn(),
}));

describe("AuditLog Component", () => {
  it("renders without crashing", () => {
    (ExecutionsApi.getExecutions as Mock).mockResolvedValue(mockExecutions);
    render(<AuditLog />);
    expect(screen.getByRole("list")).toBeInTheDocument();
  });

  it("renders execution items after fetch", async () => {
    (ExecutionsApi.getExecutions as Mock).mockResolvedValue(mockExecutions);

    render(
      <MemoryRouter>
        <AuditLog />
      </MemoryRouter>,
    );

    // Wait for the mock items to be displayed
    await waitFor(() => {
      const items = screen.getAllByRole("listitem");
      expect(items).toHaveLength(mockExecutions.executions.length);
      expect(
        screen.getByText(mockExecutions.executions[0].name),
      ).toBeInTheDocument();
      expect(
        screen.getByText(mockExecutions.executions[0].statement),
      ).toBeInTheDocument();
      expect(
        screen.getByText(mockExecutions.executions[0].connectionId),
      ).toBeInTheDocument();
    });
  });
});
