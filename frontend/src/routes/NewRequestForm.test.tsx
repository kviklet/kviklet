import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";
import ConnectionChooser from "./NewRequest";

const connection = {
  _type: "DATASOURCE",
  id: "pg-connection",
  displayName: "Postgres",
  type: "POSTGRESQL",
  protocol: "POSTGRESQL",
  maxExecutions: null,
  authenticationType: "USER_PASSWORD",
  username: "test",
  hostname: "localhost",
  port: 5432,
  description: "A test database connection",
  databaseName: "testdb",
  reviewConfig: { numTotalRequired: 1 },
  dumpsEnabled: false,
  temporaryAccessEnabled: true,
  explainEnabled: false,
  dryRunEnabled: false,
  dryRunRequiresApproval: true,
  roleArn: null,
  maxTemporaryAccessDuration: null,
  storeResults: false,
  category: null,
  permissions: [
    "datasource_connection:get",
    "execution_request:get",
    "execution_request:edit",
  ],
};

let submitted: unknown[] = [];

const server = setupServer(
  http.get("http://localhost:8081/connections/", () =>
    HttpResponse.json([connection]),
  ),
  http.get("http://localhost:8081/kubernetes/pods", () =>
    HttpResponse.json({ pods: [] }),
  ),
  http.post(
    "http://localhost:8081/execution-requests/",
    async ({ request }) => {
      submitted.push(await request.json());
      return HttpResponse.json({ message: "stop here" }, { status: 400 });
    },
  ),
);

beforeAll(() => server.listen());
afterEach(() => {
  submitted = [];
  server.resetHandlers();
});
afterAll(() => server.close());

const openForm = async (button: "query" | "access") => {
  render(
    <MemoryRouter>
      <ConnectionChooser />
    </MemoryRouter>,
  );
  await waitFor(() => {
    expect(screen.getByText("Postgres")).toBeInTheDocument();
  });
  fireEvent.click(screen.getByTestId(`${button}-button-Postgres`));
  await waitFor(() => {
    expect(screen.getByTestId("request-title")).toBeInTheDocument();
  });
};

describe("NewRequest - datasource request form", () => {
  test("submits a query request", async () => {
    await openForm("query");

    userEvent.type(screen.getByTestId("request-title"), "Count users");
    userEvent.type(screen.getByTestId("request-description"), "Quick check");
    userEvent.type(
      screen.getByTestId("request-statement"),
      "SELECT count(*) FROM users",
    );
    fireEvent.click(screen.getByTestId("submit-button"));

    await waitFor(() => expect(submitted).toHaveLength(1));
    expect(submitted[0]).toEqual({
      connectionType: "DATASOURCE",
      connectionId: "pg-connection",
      type: "SingleExecution",
      title: "Count users",
      description: "Quick check",
      statement: "SELECT count(*) FROM users",
    });
  });

  test("shows the custom message and does not submit without a title", async () => {
    await openForm("query");

    userEvent.type(screen.getByTestId("request-statement"), "SELECT 1");
    fireEvent.click(screen.getByTestId("submit-button"));

    expect(await screen.findByText("Title is required")).toBeInTheDocument();
    expect(submitted).toHaveLength(0);
  });

  test("coerces a typed duration to a number", async () => {
    await openForm("access");

    userEvent.type(screen.getByTestId("request-title"), "Debugging");
    const duration = screen.getByLabelText("Duration (in minutes)");
    userEvent.clear(duration);
    userEvent.type(duration, "90");
    fireEvent.click(screen.getByTestId("submit-button"));

    await waitFor(() => expect(submitted).toHaveLength(1));
    expect(submitted[0]).toMatchObject({ temporaryAccessDuration: 90 });
  });

  test("submits infinite temporary access as a null duration", async () => {
    await openForm("access");

    userEvent.type(screen.getByTestId("request-title"), "Debugging");
    fireEvent.click(screen.getByLabelText(/infinite/i));
    fireEvent.click(screen.getByTestId("submit-button"));

    await waitFor(() => expect(submitted).toHaveLength(1));
    expect(submitted[0]).toMatchObject({
      type: "TemporaryAccess",
      temporaryAccessDuration: null,
    });
  });
});
