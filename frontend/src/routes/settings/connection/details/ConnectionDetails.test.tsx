import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, MockedFunction, test, vi } from "vitest";
import ConnectionDetails from "./ConnectionDetails";
import {
  DatabaseConnectionResponse,
  getCategories,
  getConnection,
  KubernetesConnectionResponse,
  patchConnection,
} from "../../../../api/DatasourceApi";

// Keep the real enums and schemas; only the network calls are replaced.
vi.mock("../../../../api/DatasourceApi", async (importOriginal) => ({
  ...(await importOriginal<typeof import("../../../../api/DatasourceApi")>()),
  getConnection: vi.fn(),
  patchConnection: vi.fn(),
  getCategories: vi.fn(),
}));

const mockGetConnection = getConnection as MockedFunction<typeof getConnection>;
const mockPatchConnection = patchConnection as MockedFunction<
  typeof patchConnection
>;
const mockGetCategories = getCategories as MockedFunction<typeof getCategories>;

const datasourceConnection = {
  _type: "DATASOURCE",
  id: "pg-prod",
  displayName: "Postgres Prod",
  type: "POSTGRESQL",
  protocol: "POSTGRESQL",
  maxExecutions: 1,
  authenticationType: "USER_PASSWORD",
  username: "kviklet",
  hostname: "db.internal",
  port: 5432,
  description: "Production database",
  databaseName: null,
  reviewConfig: { numTotalRequired: 1 },
  additionalJDBCOptions: undefined,
  dumpsEnabled: false,
  temporaryAccessEnabled: true,
  explainEnabled: false,
  dryRunEnabled: false,
  dryRunRequiresApproval: true,
  roleArn: null,
  maxTemporaryAccessDuration: 60,
  storeResults: false,
  category: "prod",
  permissions: ["datasource_connection:edit"],
} as DatabaseConnectionResponse;

const kubernetesConnection = {
  _type: "KUBERNETES",
  id: "k8s-prod",
  displayName: "Kubernetes Prod",
  description: "Production cluster",
  reviewConfig: { numTotalRequired: 1 },
  maxExecutions: 1,
  temporaryAccessEnabled: true,
  storeResults: false,
  category: null,
  kubernetesExecInitialWaitTimeoutSeconds: 5,
  kubernetesExecTimeoutMinutes: 60,
  permissions: ["datasource_connection:edit"],
} as KubernetesConnectionResponse;

const renderPage = async (id: string) => {
  render(
    <MemoryRouter initialEntries={[`/settings/connections/${id}`]}>
      <Routes>
        <Route
          path="/settings/connections/:connectionId"
          element={<ConnectionDetails />}
        />
      </Routes>
    </MemoryRouter>,
  );
  await screen.findByTestId("edit-connection-form");
};

const replaceValue = (element: HTMLElement, value: string) => {
  userEvent.clear(element);
  if (value !== "") {
    userEvent.type(element, value);
  }
};

const save = () =>
  fireEvent.click(screen.getByTestId("save-connection-button"));

describe("ConnectionDetails", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockGetCategories.mockResolvedValue([]);
    mockGetConnection.mockImplementation((id) =>
      Promise.resolve(
        id === "k8s-prod" ? kubernetesConnection : datasourceConnection,
      ),
    );
    mockPatchConnection.mockImplementation(() =>
      Promise.resolve(datasourceConnection),
    );
  });

  test("saves an edited database connection", async () => {
    await renderPage("pg-prod");
    expect(screen.getByTestId("connection-id")).toHaveTextContent("pg-prod");
    expect(screen.getByTestId("save-connection-button")).toBeDisabled();

    replaceValue(screen.getByTestId("connection-name"), "Postgres Primary");
    save();

    await waitFor(() => expect(mockPatchConnection).toHaveBeenCalledTimes(1));
    expect(mockPatchConnection.mock.calls[0][1]).toBe("pg-prod");
    expect(mockPatchConnection.mock.calls[0][0]).toEqual({
      connectionType: "DATASOURCE",
      displayName: "Postgres Primary",
      description: "Production database",
      category: "prod",
      clearCategory: false,
      type: "POSTGRESQL",
      protocol: "POSTGRESQL",
      hostname: "db.internal",
      port: 5432,
      databaseName: "",
      additionalJDBCOptions: "",
      username: "kviklet",
      // An untouched password field must not overwrite the stored password.
      password: undefined,
      authenticationType: "USER_PASSWORD",
      reviewConfig: { numTotalRequired: 1 },
      maxExecutions: 1,
      temporaryAccessEnabled: true,
      maxTemporaryAccessDuration: 60,
      clearMaxTempDuration: false,
      dryRunEnabled: false,
      dryRunRequiresApproval: true,
      explainEnabled: false,
      dumpsEnabled: false,
      storeResults: false,
    });
  });

  test("removes the limits when their fields are cleared", async () => {
    await renderPage("pg-prod");
    replaceValue(
      screen.getByTestId("connection-max-temporary-access-duration"),
      "",
    );
    replaceValue(screen.getByTestId("connection-max-executions"), "");
    save();

    await waitFor(() => expect(mockPatchConnection).toHaveBeenCalledTimes(1));
    expect(mockPatchConnection.mock.calls[0][0]).toMatchObject({
      maxTemporaryAccessDuration: null,
      clearMaxTempDuration: true,
      maxExecutions: 0,
    });
  });

  test("sends a new password and typed numbers", async () => {
    await renderPage("pg-prod");
    userEvent.type(screen.getByTestId("connection-password"), "s3cret");
    replaceValue(screen.getByTestId("connection-port"), "6543");
    replaceValue(screen.getByTestId("connection-required-reviews"), "2");
    save();

    await waitFor(() => expect(mockPatchConnection).toHaveBeenCalledTimes(1));
    expect(mockPatchConnection.mock.calls[0][0]).toMatchObject({
      password: "s3cret",
      port: 6543,
      reviewConfig: { numTotalRequired: 2 },
    });
  });

  test("keeps the form valid when the database type changes", async () => {
    await renderPage("pg-prod");
    userEvent.selectOptions(screen.getByTestId("connection-type"), "MONGODB");
    save();

    await waitFor(() => expect(mockPatchConnection).toHaveBeenCalledTimes(1));
    expect(mockPatchConnection.mock.calls[0][0]).toMatchObject({
      type: "MONGODB",
      protocol: "MONGODB",
      authenticationType: "USER_PASSWORD",
    });
  });

  test("returns to a clean state after a successful save", async () => {
    mockPatchConnection.mockResolvedValue({
      ...datasourceConnection,
      displayName: "Postgres Primary",
    });
    await renderPage("pg-prod");
    replaceValue(screen.getByTestId("connection-name"), "Postgres Primary");
    expect(screen.getByTestId("save-connection-button")).toBeEnabled();
    save();

    await waitFor(() =>
      expect(screen.getByTestId("save-connection-button")).toBeDisabled(),
    );
    expect(screen.getByTestId("connection-name")).toHaveValue(
      "Postgres Primary",
    );
  });

  test("keeps the input when saving fails", async () => {
    mockPatchConnection.mockResolvedValue({ message: "Host unreachable" });
    await renderPage("pg-prod");
    replaceValue(screen.getByTestId("connection-name"), "Postgres Primary");
    save();

    await waitFor(() => expect(mockPatchConnection).toHaveBeenCalledTimes(1));
    await waitFor(() =>
      expect(screen.getByTestId("save-connection-button")).toBeEnabled(),
    );
    expect(screen.getByTestId("connection-name")).toHaveValue(
      "Postgres Primary",
    );
  });

  test("saves an edited Kubernetes connection", async () => {
    await renderPage("k8s-prod");
    expect(screen.queryByTestId("connection-hostname")).not.toBeInTheDocument();

    replaceValue(screen.getByTestId("connection-name"), "Kubernetes Primary");
    replaceValue(screen.getByTestId("kubernetes-exec-timeout-minutes"), "30");
    save();

    await waitFor(() => expect(mockPatchConnection).toHaveBeenCalledTimes(1));
    expect(mockPatchConnection.mock.calls[0][0]).toEqual({
      connectionType: "KUBERNETES",
      displayName: "Kubernetes Primary",
      description: "Production cluster",
      category: null,
      clearCategory: true,
      reviewConfig: { numTotalRequired: 1 },
      maxExecutions: 1,
      storeResults: false,
      kubernetesExecInitialWaitTimeoutSeconds: 5,
      kubernetesExecTimeoutMinutes: 30,
    });
  });

  test("is read-only without the edit permission", async () => {
    mockGetConnection.mockResolvedValue({
      ...datasourceConnection,
      permissions: ["datasource_connection:get"],
    });
    await renderPage("pg-prod");

    expect(screen.getByTestId("read-only-badge")).toBeInTheDocument();
    expect(screen.getByTestId("connection-name")).toBeDisabled();
    expect(screen.getByTestId("connection-temporary-access")).toBeDisabled();
    expect(
      screen.queryByTestId("save-connection-button"),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId("delete-connection-button"),
    ).not.toBeInTheDocument();
  });
});
