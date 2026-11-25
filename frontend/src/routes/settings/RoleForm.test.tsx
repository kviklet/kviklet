import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, MockedFunction, test, vi } from "vitest";
import RoleForm from "./RoleForm";
import { Role } from "../../hooks/roles";
import { ConnectionResponse, getConnections } from "../../api/DatasourceApi";

// Keep the real enums and schemas; only the network call is replaced.
vi.mock("../../api/DatasourceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../api/DatasourceApi")>()),
  getConnections: vi.fn(),
}));

const mockGetConnections = getConnections as MockedFunction<
  typeof getConnections
>;
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

const newRole: Role = {
  id: "",
  name: "",
  description: "",
  isAdmin: false,
  userPolicy: { read: false, create: false, editSelf: false },
  rolePolicy: { read: false },
  connectionPolicies: [],
};

const connection = (id: string, displayName: string) =>
  ({ id, displayName }) as ConnectionResponse;

const submit = () => fireEvent.click(screen.getByTestId("role-submit-button"));

describe("RoleForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetConnections.mockResolvedValue([
      connection("pg-prod", "Postgres Prod"),
      connection("pg-staging", "Postgres Staging"),
      connection("mysql-prod", "MySQL Prod"),
    ]);
  });

  test("saves an edited role", async () => {
    render(<RoleForm role={role} onSubmit={mockOnSubmit} />);
    expect(screen.getByTestId("role-submit-button")).toBeDisabled();

    fireEvent.click(screen.getByTestId("role-role-policy-read"));
    submit();

    await waitFor(() => expect(mockOnSubmit).toHaveBeenCalledTimes(1));
    expect(mockOnSubmit.mock.calls[0][0]).toEqual({
      ...role,
      rolePolicy: { read: true },
    });
  });

  test("shows which connections a pattern matches", async () => {
    render(<RoleForm role={role} onSubmit={mockOnSubmit} />);
    await waitFor(() =>
      expect(
        screen.getByTestId("connection-selector-matches"),
      ).toHaveTextContent(
        "Matches 2 of 3 connections: Postgres Prod, Postgres Staging",
      ),
    );

    const selector = screen.getByTestId("connection-selector-input");
    userEvent.clear(selector);
    userEvent.type(selector, "oracle-*");
    expect(screen.getByTestId("connection-selector-matches")).toHaveTextContent(
      "Matches no connection yet.",
    );
  });

  test("write and review include read", () => {
    render(<RoleForm role={newRole} onSubmit={mockOnSubmit} />);
    fireEvent.click(screen.getByTestId("role-add-connection-policy-button"));
    const read = screen.getByTestId(
      "role-connection-policy-0-execution_request_read",
    );
    expect(read).not.toBeChecked();

    fireEvent.click(
      screen.getByTestId("role-connection-policy-0-execution_request_write"),
    );
    expect(read).toBeChecked();
    expect(read).toBeDisabled();
  });

  test("creates a role with a connection rule", async () => {
    render(
      <RoleForm
        role={newRole}
        onSubmit={mockOnSubmit}
        submitLabel="Create role"
      />,
    );
    userEvent.type(screen.getByLabelText("Name"), "Reviewers");
    fireEvent.click(screen.getByTestId("role-add-connection-policy-button"));
    userEvent.type(screen.getByTestId("connection-selector-input"), "pg-*");
    fireEvent.click(
      screen.getByTestId("role-connection-policy-0-execution_request_review"),
    );
    submit();

    await waitFor(() => expect(mockOnSubmit).toHaveBeenCalledTimes(1));
    expect(mockOnSubmit.mock.calls[0][0]).toMatchObject({
      name: "Reviewers",
      connectionPolicies: [
        {
          selector: "pg-*",
          execution_request_read: false,
          execution_request_write: false,
          execution_request_review: true,
        },
      ],
    });
  });

  test("requires a name and a pattern for every rule", async () => {
    render(<RoleForm role={newRole} onSubmit={mockOnSubmit} />);
    fireEvent.click(screen.getByTestId("role-add-connection-policy-button"));
    submit();

    await waitFor(() =>
      expect(screen.getAllByText("Required")).toHaveLength(2),
    );
    expect(mockOnSubmit).not.toHaveBeenCalled();
  });

  test("an admin role hides the other permissions and keeps them when switched back", () => {
    render(<RoleForm role={role} onSubmit={mockOnSubmit} />);
    fireEvent.click(screen.getByTestId("role-is-admin"));
    expect(
      screen.queryByTestId("role-user-policy-read"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("role-connection-access"),
    ).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("role-is-admin"));
    expect(screen.getByTestId("role-user-policy-read")).toBeChecked();
    expect(screen.getByTestId("role-user-policy-editSelf")).toBeChecked();
    expect(screen.getByTestId("connection-selector-input")).toHaveValue("pg-*");
  });

  test("renders read-only without a submit button", () => {
    render(
      <fieldset>
        <RoleForm role={role} onSubmit={mockOnSubmit} readOnly />
      </fieldset>,
    );
    expect(screen.getByLabelText("Name")).toBeDisabled();
    expect(screen.getByTestId("role-user-policy-read")).toBeDisabled();
    expect(screen.queryByTestId("role-submit-button")).not.toBeInTheDocument();
  });
});
