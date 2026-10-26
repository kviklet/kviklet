import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, test, vi } from "vitest";
import RoleForm from "./RoleForm";
import { Role } from "../../hooks/roles";

// The real selector is a Headless UI combobox backed by a connections fetch;
// a plain input is enough to drive the form.
vi.mock("./ConnectionSelector", () => ({
  default: ({
    value,
    onChange,
  }: {
    value: string;
    onChange: (value: string) => void;
  }) => (
    <input
      data-testid="connection-selector"
      value={value}
      onChange={(event) => onChange(event.target.value)}
    />
  ),
}));

const mockOnSubmit = vi.fn();

const role: Role = {
  id: "role-1",
  name: "Developers",
  description: null,
  isAdmin: false,
  userPolicy: { read: true, create: false, editSelf: true },
  rolePolicy: { read: false },
  connectionPolicies: [
    {
      selector: "pg-*",
      execution_request_read: true,
      execution_request_write: false,
      execution_request_review: false,
    },
  ],
};

describe("RoleForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  test("submits the unchanged role", async () => {
    render(<RoleForm role={role} onSubmit={mockOnSubmit} />);

    fireEvent.click(screen.getByTestId("role-submit-button"));

    await waitFor(() => expect(mockOnSubmit).toHaveBeenCalledTimes(1));
    expect(mockOnSubmit.mock.calls[0][0]).toEqual(role);
  });
});
