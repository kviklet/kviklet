import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, MockedFunction, test, vi } from "vitest";
import NewConnection from "./NewConnection";
import {
  addConnection,
  ConnectionResponse,
  getCategories,
  getConnections,
  testConnection,
} from "../../../api/DatasourceApi";

// Keep the real enums and schemas; only the network calls are replaced.
vi.mock("../../../api/DatasourceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../api/DatasourceApi")>()),
  addConnection: vi.fn(),
  testConnection: vi.fn(),
  getConnections: vi.fn(),
  getCategories: vi.fn(),
}));

const mockAddConnection = addConnection as MockedFunction<typeof addConnection>;
const mockTestConnection = testConnection as MockedFunction<
  typeof testConnection
>;
const mockGetConnections = getConnections as MockedFunction<
  typeof getConnections
>;
const mockGetCategories = getCategories as MockedFunction<typeof getCategories>;

const renderPage = (path = "/settings/connections/new") =>
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/settings/connections/new" element={<NewConnection />} />
        <Route
          path="/settings/connections"
          element={<div data-testid="connections-list" />}
        />
      </Routes>
    </MemoryRouter>,
  );

const replaceValue = (element: HTMLElement, value: string) => {
  userEvent.clear(element);
  if (value !== "") {
    userEvent.type(element, value);
  }
};

const fillDatabaseFields = () => {
  userEvent.type(screen.getByTestId("connection-hostname"), "localhost");
  userEvent.type(screen.getByTestId("connection-username"), "testuser");
  userEvent.type(screen.getByTestId("connection-password"), "testpass");
};

const submit = () =>
  fireEvent.click(screen.getByTestId("create-connection-button"));

describe("NewConnection", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetConnections.mockResolvedValue([]);
    mockGetCategories.mockResolvedValue([]);
    mockAddConnection.mockResolvedValue({} as ConnectionResponse);
  });

  test("creates a Postgres connection with the defaults and returns to the list", async () => {
    renderPage();
    userEvent.type(screen.getByTestId("connection-name"), "Test Connection");
    userEvent.type(screen.getByTestId("connection-description"), "Desc");
    fillDatabaseFields();
    submit();

    await waitFor(() => expect(mockAddConnection).toHaveBeenCalledTimes(1));
    expect(mockAddConnection.mock.calls[0][0]).toEqual({
      connectionType: "DATASOURCE",
      displayName: "Test Connection",
      id: "test-connection",
      description: "Desc",
      category: null,
      type: "POSTGRESQL",
      protocol: "POSTGRESQL",
      hostname: "localhost",
      port: 5432,
      databaseName: "",
      additionalJDBCOptions: "",
      reviewConfig: { numTotalRequired: 1 },
      maxExecutions: 1,
      temporaryAccessEnabled: true,
      maxTemporaryAccessDuration: null,
      dryRunEnabled: false,
      dryRunRequiresApproval: true,
      explainEnabled: false,
      dumpsEnabled: false,
      storeResults: false,
      authenticationType: "USER_PASSWORD",
      username: "testuser",
      password: "testpass",
    });
    await waitFor(() =>
      expect(screen.getByTestId("connections-list")).toBeInTheDocument(),
    );
  });

  test("shows every field without an advanced section", () => {
    renderPage();
    expect(screen.getByTestId("connection-id")).toBeVisible();
    expect(screen.getByTestId("connection-port")).toBeVisible();
    expect(screen.getByTestId("connection-database")).toBeVisible();
    expect(screen.getByTestId("test-connection-button")).toBeVisible();
    expect(screen.queryByText("Advanced Options")).not.toBeInTheDocument();
  });

  test("derives the id from the name until the id is edited", () => {
    renderPage();
    const name = screen.getByTestId("connection-name");
    const id = screen.getByTestId("connection-id");

    userEvent.type(name, "Orders Prod");
    expect(id).toHaveValue("orders-prod");

    replaceValue(id, "orders");
    userEvent.type(name, " EU");
    expect(id).toHaveValue("orders");
  });

  test("preselects the category from the link", () => {
    mockGetCategories.mockResolvedValue(["prod"]);
    renderPage("/settings/connections/new?category=prod");
    expect(screen.getByDisplayValue("prod")).toBeInTheDocument();
  });

  test("switching the type follows that type's defaults", () => {
    renderPage();
    fireEvent.click(
      screen.getByTestId("connection-type-MYSQL").querySelector("input")!,
    );
    expect(screen.getByTestId("connection-port")).toHaveValue(3306);
    expect(screen.getByTestId("connection-additional-options")).toHaveValue(
      "?allowMultiQueries=true",
    );
    expect(screen.getByTestId("connection-dumps")).toBeInTheDocument();

    fireEvent.click(
      screen.getByTestId("connection-type-MONGODB").querySelector("input")!,
    );
    expect(screen.getByTestId("connection-port")).toHaveValue(27017);
    expect(screen.getByTestId("connection-additional-options")).toHaveValue("");
    expect(screen.getByTestId("connection-protocol")).toBeInTheDocument();
    expect(screen.queryByTestId("connection-dumps")).not.toBeInTheDocument();
    expect(screen.queryByTestId("connection-dry-run")).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("connection-authentication-type"),
    ).not.toBeInTheDocument();
  });

  test("keeps a port the user entered when the type changes", () => {
    renderPage();
    replaceValue(screen.getByTestId("connection-port"), "6543");
    fireEvent.click(
      screen.getByTestId("connection-type-MYSQL").querySelector("input")!,
    );
    expect(screen.getByTestId("connection-port")).toHaveValue(6543);
  });

  test("sends empty limits as unlimited and coerces typed numbers", async () => {
    renderPage();
    userEvent.type(screen.getByTestId("connection-name"), "Test Connection");
    fillDatabaseFields();
    replaceValue(screen.getByTestId("connection-max-executions"), "");
    replaceValue(
      screen.getByTestId("connection-max-temporary-access-duration"),
      "120",
    );
    replaceValue(screen.getByTestId("connection-required-reviews"), "2");
    submit();

    await waitFor(() => expect(mockAddConnection).toHaveBeenCalledTimes(1));
    expect(mockAddConnection.mock.calls[0][0]).toMatchObject({
      maxExecutions: 0,
      maxTemporaryAccessDuration: 120,
      reviewConfig: { numTotalRequired: 2 },
    });
  });

  test("keeps the database settings when switching to Kubernetes and back", () => {
    renderPage();
    userEvent.type(screen.getByTestId("connection-hostname"), "localhost");
    fireEvent.click(screen.getByTestId("connection-explain"));
    fireEvent.click(
      screen.getByTestId("connection-type-KUBERNETES").querySelector("input")!,
    );
    fireEvent.click(
      screen.getByTestId("connection-type-POSTGRESQL").querySelector("input")!,
    );

    expect(screen.getByTestId("connection-hostname")).toHaveValue("localhost");
    expect(screen.getByTestId("connection-temporary-access")).toBeChecked();
    expect(screen.getByTestId("connection-explain")).toBeChecked();
    expect(screen.getByTestId("connection-store-results")).not.toBeChecked();
  });

  test("creates a Kubernetes connection", async () => {
    renderPage("/settings/connections/new?category=prod");
    fireEvent.click(
      screen.getByTestId("connection-type-KUBERNETES").querySelector("input")!,
    );
    expect(screen.queryByTestId("connection-hostname")).not.toBeInTheDocument();

    userEvent.type(screen.getByTestId("connection-name"), "My Pods");
    userEvent.type(
      screen.getByTestId("connection-description"),
      "Prod cluster",
    );
    replaceValue(screen.getByTestId("connection-required-reviews"), "3");
    replaceValue(screen.getByTestId("kubernetes-exec-timeout-minutes"), "15");
    submit();

    await waitFor(() => expect(mockAddConnection).toHaveBeenCalledTimes(1));
    expect(mockAddConnection.mock.calls[0][0]).toEqual({
      connectionType: "KUBERNETES",
      displayName: "My Pods",
      id: "my-pods",
      description: "Prod cluster",
      category: "prod",
      reviewConfig: { numTotalRequired: 3 },
      maxExecutions: 1,
      storeResults: false,
      kubernetesExecInitialWaitTimeoutSeconds: 5,
      kubernetesExecTimeoutMinutes: 15,
    });
  });

  test("shows validation errors next to the fields and does not submit", async () => {
    renderPage();
    submit();

    await waitFor(() =>
      expect(screen.getAllByText("At least 3 characters")).toHaveLength(2),
    );
    expect(screen.getByText("Required")).toBeInTheDocument();
    expect(mockAddConnection).not.toHaveBeenCalled();
  });

  test("flags an id that another connection already uses", async () => {
    mockGetConnections.mockResolvedValue([
      { id: "orders-prod" } as ConnectionResponse,
    ]);
    renderPage();
    userEvent.type(screen.getByTestId("connection-name"), "Orders Prod");

    await waitFor(() =>
      expect(
        screen.getByText("Another connection already uses this ID"),
      ).toBeInTheDocument(),
    );
    expect(screen.getByTestId("create-connection-button")).toBeDisabled();
  });

  test("stays on the form with the input intact when creation fails", async () => {
    mockAddConnection.mockResolvedValue({ message: "Host unreachable" });
    renderPage();
    userEvent.type(screen.getByTestId("connection-name"), "Test Connection");
    fillDatabaseFields();
    submit();

    await waitFor(() => expect(mockAddConnection).toHaveBeenCalledTimes(1));
    expect(screen.queryByTestId("connections-list")).not.toBeInTheDocument();
    expect(screen.getByTestId("connection-name")).toHaveValue(
      "Test Connection",
    );
    expect(screen.getByTestId("connection-hostname")).toHaveValue("localhost");
  });

  test("tests the database settings before the rest of the form is filled in", async () => {
    mockTestConnection.mockResolvedValue({
      success: true,
      details: "",
      accessibleDatabases: ["orders"],
    });
    renderPage();
    fillDatabaseFields();
    fireEvent.click(screen.getByTestId("test-connection-button"));

    await waitFor(() =>
      expect(screen.getByTestId("test-connection-success")).toBeInTheDocument(),
    );
    expect(mockTestConnection.mock.calls[0][0]).toMatchObject({
      hostname: "localhost",
      port: 5432,
      username: "testuser",
      password: "testpass",
    });
    // The untouched name field must not show an error because of the test.
    expect(screen.queryByText("At least 3 characters")).not.toBeInTheDocument();
  });

  test("shows why a connection test failed", async () => {
    mockTestConnection.mockResolvedValue({
      success: false,
      details: "password authentication failed",
      accessibleDatabases: [],
    });
    renderPage();
    fillDatabaseFields();
    fireEvent.click(screen.getByTestId("test-connection-button"));

    await waitFor(() =>
      expect(screen.getByTestId("test-connection-failure")).toBeInTheDocument(),
    );
    expect(
      screen.getByText("password authentication failed"),
    ).toBeInTheDocument();
  });

  test("creates one connection per selected database", async () => {
    mockTestConnection.mockResolvedValue({
      success: true,
      details: "",
      accessibleDatabases: ["orders", "billing", "audit"],
    });
    renderPage();
    userEvent.type(screen.getByTestId("connection-name"), "Prod");
    fillDatabaseFields();
    fireEvent.click(screen.getByTestId("test-connection-button"));
    await waitFor(() =>
      expect(screen.getByTestId("create-per-database")).toBeInTheDocument(),
    );

    fireEvent.click(screen.getByTestId("create-per-database"));
    fireEvent.click(screen.getByLabelText("audit"));
    expect(screen.getByTestId("create-connection-button")).toHaveTextContent(
      "Create 2 connections",
    );
    submit();

    await waitFor(() => expect(mockAddConnection).toHaveBeenCalledTimes(2));
    expect(mockAddConnection.mock.calls.map((call) => call[0])).toMatchObject([
      {
        displayName: "Prod - orders",
        id: "prod-orders",
        databaseName: "orders",
      },
      {
        displayName: "Prod - billing",
        id: "prod-billing",
        databaseName: "billing",
      },
    ]);
  });
});
