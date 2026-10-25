import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, test, vi } from "vitest";
import {
  DatabaseConnectionResponse,
  KubernetesConnectionResponse,
} from "../../../../api/DatasourceApi";
import UpdateDatasourceConnectionForm from "./UpdateDatasourceConnectionForm";
import UpdateKubernetesConnectionForm from "./UpdateKubernetesConnectionForm";

const mockEditConnection = vi.fn();

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
  maxTemporaryAccessDuration: null,
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

const replaceValue = (element: HTMLElement, value: string) => {
  userEvent.clear(element);
  userEvent.type(element, value);
};

const settle = () => new Promise((resolve) => setTimeout(resolve, 100));

describe("UpdateDatasourceConnectionForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderForm = () =>
    render(
      <UpdateDatasourceConnectionForm
        connection={datasourceConnection}
        editConnection={mockEditConnection}
      />,
    );

  test("submits the edited form values", async () => {
    renderForm();

    replaceValue(screen.getByLabelText("Name"), "Postgres Primary");
    fireEvent.click(screen.getByText("Save"));

    await waitFor(() => expect(mockEditConnection).toHaveBeenCalledTimes(1));
    expect(mockEditConnection.mock.calls[0][0]).toEqual({
      connectionType: "DATASOURCE",
      displayName: "Postgres Primary",
      description: "Production database",
      type: "POSTGRESQL",
      protocol: "POSTGRESQL",
      hostname: "db.internal",
      port: 5432,
      username: "kviklet",
      password: "",
      databaseName: "",
      reviewConfig: { numTotalRequired: 1 },
      additionalJDBCOptions: "",
      maxExecutions: 1,
      authenticationType: "USER_PASSWORD",
      dumpsEnabled: false,
      temporaryAccessEnabled: true,
      explainEnabled: false,
      dryRunEnabled: false,
      dryRunRequiresApproval: true,
      maxTemporaryAccessDuration: null,
      roleArn: null,
      storeResults: false,
      category: "prod",
    });
  });

  test("submits a typed port as entered", async () => {
    renderForm();

    replaceValue(screen.getByLabelText("Port"), "6543");
    fireEvent.click(screen.getByText("Save"));

    await waitFor(() => expect(mockEditConnection).toHaveBeenCalledTimes(1));
    // The edit hook submits the raw form values, not the parsed output.
    expect(mockEditConnection.mock.calls[0][0]).toMatchObject({
      port: "6543",
    });
  });

  test("does not submit an invalid name", async () => {
    renderForm();

    replaceValue(screen.getByLabelText("Name"), "ab");
    fireEvent.click(screen.getByText("Save"));

    await settle();
    expect(mockEditConnection).not.toHaveBeenCalled();
  });
});

describe("UpdateKubernetesConnectionForm", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderForm = () =>
    render(
      <UpdateKubernetesConnectionForm
        connection={kubernetesConnection}
        editConnection={mockEditConnection}
      />,
    );

  test("submits the edited form values", async () => {
    renderForm();

    replaceValue(
      screen.getByTestId("kubernetes-connection-name"),
      "Kubernetes Primary",
    );
    fireEvent.click(screen.getByTestId("save-kubernetes-connection-button"));

    await waitFor(() => expect(mockEditConnection).toHaveBeenCalledTimes(1));
    expect(mockEditConnection.mock.calls[0][0]).toEqual({
      connectionType: "KUBERNETES",
      displayName: "Kubernetes Primary",
      description: "Production cluster",
      reviewConfig: { numTotalRequired: 1 },
      maxExecutions: 1,
      storeResults: false,
      kubernetesExecInitialWaitTimeoutSeconds: 5,
      kubernetesExecTimeoutMinutes: 60,
      category: null,
    });
  });

  test("does not submit a timeout below the minimum", async () => {
    renderForm();

    fireEvent.click(screen.getByTestId("kubernetes-advanced-options-button"));
    replaceValue(screen.getByTestId("kubernetes-exec-timeout-minutes"), "0");
    fireEvent.click(screen.getByTestId("save-kubernetes-connection-button"));

    await settle();
    expect(mockEditConnection).not.toHaveBeenCalled();
  });
});
