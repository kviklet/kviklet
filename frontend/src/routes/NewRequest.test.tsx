import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { http, HttpResponse, delay } from "msw";
import { setupServer } from "msw/node";
import ConnectionChooser from "./NewRequest";
import { UserStatusContext } from "../components/UserStatusProvider";
import { StatusResponse } from "../api/StatusApi";

const mockConnections = [
  {
    id: "test-connection-1",
    displayName: "Test Database Connection",
    description: "A test database for development",
    _type: "DATASOURCE" as const,
    type: "POSTGRESQL" as const,
    protocol: "POSTGRESQL" as const,
    maxExecutions: null,
    authenticationType: "USER_PASSWORD" as const,
    username: "testuser",
    hostname: "localhost",
    port: 5432,
    databaseName: "testdb",
    reviewConfig: { numTotalRequired: 1 },
    dumpsEnabled: true,
    temporaryAccessEnabled: true,
    explainEnabled: true,
    dryRunEnabled: false,
    dryRunRequiresApproval: true,
    roleArn: null,
    storeResults: false,
    category: null,
    permissions: [
      "datasource_connection:get",
      "execution_request:get",
      "execution_request:edit",
    ],
  },
  {
    id: "test-connection-2",
    displayName: "Another Test Connection with a Very Long Name",
    description:
      "Another test database with a very long description to test truncation",
    _type: "DATASOURCE" as const,
    type: "MYSQL" as const,
    protocol: "MYSQL" as const,
    maxExecutions: null,
    authenticationType: "USER_PASSWORD" as const,
    username: "testuser",
    hostname: "localhost",
    port: 3306,
    databaseName: "testdb",
    reviewConfig: { numTotalRequired: 1 },
    dumpsEnabled: false,
    temporaryAccessEnabled: true,
    explainEnabled: false,
    dryRunEnabled: false,
    dryRunRequiresApproval: true,
    roleArn: null,
    storeResults: false,
    category: null,
    permissions: [
      "datasource_connection:get",
      "execution_request:get",
      "execution_request:edit",
    ],
  },
];

describe("NewRequest - Basic functionality", () => {
  test("renders the new request page", async () => {
    // Mock an empty connections response to avoid complex setup
    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json([], { status: 200 });
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
    );

    server.listen();

    render(
      <MemoryRouter>
        <ConnectionChooser />
      </MemoryRouter>,
    );

    // Check that the page title is rendered (generic, since no connection is chosen yet)
    expect(screen.getByText("Request Access")).toBeInTheDocument();

    // Wait for loading to complete and connections section to appear
    await waitFor(() => {
      expect(screen.getByText("Connections")).toBeInTheDocument();
    });

    server.close();
  });

  test("displays view mode toggle buttons", async () => {
    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json(mockConnections, { status: 200 });
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
    );

    server.listen();

    render(
      <MemoryRouter>
        <ConnectionChooser />
      </MemoryRouter>,
    );

    // Wait for connections to load
    await waitFor(() => {
      expect(screen.getByText("Test Database Connection")).toBeInTheDocument();
    });

    // Check for view mode toggle buttons
    const gridButton = screen.getByTitle("Grid view");
    const listButton = screen.getByTitle("List view");

    expect(gridButton).toBeInTheDocument();
    expect(listButton).toBeInTheDocument();

    server.close();
  });

  test("toggles between grid and list view", async () => {
    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json(mockConnections, { status: 200 });
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
    );

    server.listen();

    render(
      <MemoryRouter>
        <ConnectionChooser />
      </MemoryRouter>,
    );

    // Wait for connections to load
    await waitFor(() => {
      expect(screen.getByText("Test Database Connection")).toBeInTheDocument();
    });

    const gridButton = screen.getByTitle("Grid view");
    const listButton = screen.getByTitle("List view");

    // Grid view should be active by default
    expect(gridButton).toHaveClass("bg-indigo-600");
    expect(listButton).not.toHaveClass("bg-indigo-600");

    // Switch to list view
    fireEvent.click(listButton);

    await waitFor(() => {
      expect(listButton).toHaveClass("bg-indigo-600");
      expect(gridButton).not.toHaveClass("bg-indigo-600");
    });

    // Switch back to grid view
    fireEvent.click(gridButton);

    await waitFor(() => {
      expect(gridButton).toHaveClass("bg-indigo-600");
      expect(listButton).not.toHaveClass("bg-indigo-600");
    });

    server.close();
  });

  test("displays connections in both grid and list view", async () => {
    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json(mockConnections, { status: 200 });
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
    );

    server.listen();

    render(
      <MemoryRouter>
        <ConnectionChooser />
      </MemoryRouter>,
    );

    // Wait for connections to load
    await waitFor(() => {
      expect(screen.getByText("Test Database Connection")).toBeInTheDocument();
    });

    // Both connections should be visible in grid view
    expect(screen.getByText("Test Database Connection")).toBeInTheDocument();
    expect(
      screen.getByText("Another Test Connection with a Very Long Name"),
    ).toBeInTheDocument();

    // Switch to list view
    const listButton = screen.getByTitle("List view");
    fireEvent.click(listButton);

    // Both connections should still be visible in list view
    await waitFor(() => {
      expect(screen.getByText("Test Database Connection")).toBeInTheDocument();
      expect(
        screen.getByText("Another Test Connection with a Very Long Name"),
      ).toBeInTheDocument();
    });

    server.close();
  });
});

describe("NewRequest - Form submission", () => {
  test("disables submit button while form is submitting to prevent double submissions", async () => {
    let requestCount = 0;

    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json(
          [
            {
              _type: "DATASOURCE",
              id: "test-connection",
              displayName: "Test Database",
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
            },
          ],
          { status: 200 },
        );
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
      http.post("http://localhost:8081/execution-requests/", async () => {
        requestCount++;
        // Add delay to simulate slow network - this gives us time to check button state
        await delay(200);
        return HttpResponse.json(
          {
            _type: "DATASOURCE",
            id: "req-123",
            type: "SingleExecution",
            title: "Test Request",
            description: "",
            statement: "SELECT 1",
            connection: {
              _type: "DATASOURCE",
              id: "test-connection",
              displayName: "Test Database",
            },
            author: { id: "user-1", email: "test@test.com" },
            executionStatus: "PENDING",
            reviewStatus: "AWAITING_APPROVAL",
            createdAt: new Date().toISOString(),
            events: [],
          },
          { status: 200 },
        );
      }),
    );

    server.listen();

    render(
      <MemoryRouter>
        <ConnectionChooser />
      </MemoryRouter>,
    );

    // Wait for connections to load
    await waitFor(() => {
      expect(screen.getByText("Test Database")).toBeInTheDocument();
    });

    // Click on the Query button to select the connection and mode
    const queryButton = screen.getByTestId("query-button-Test Database");
    fireEvent.click(queryButton);

    // Wait for the form to appear
    await waitFor(() => {
      expect(screen.getByTestId("request-title")).toBeInTheDocument();
    });

    // Fill in required fields
    userEvent.type(screen.getByTestId("request-title"), "Test Request");
    userEvent.type(screen.getByTestId("request-statement"), "SELECT 1");

    // Get the submit button
    const submitButton = screen.getByTestId("submit-button");

    // Verify button is initially enabled (shows "Submit")
    expect(submitButton).toHaveTextContent("Submit");
    expect(submitButton).not.toBeDisabled();

    // Click submit
    fireEvent.click(submitButton);

    // The button should immediately show "Submitting..." and be disabled
    await waitFor(() => {
      expect(submitButton).toHaveTextContent("Submitting...");
    });

    // Try clicking the button again while it's submitting
    fireEvent.click(submitButton);
    fireEvent.click(submitButton);

    // Wait for the request to complete
    await waitFor(
      () => {
        // The request should have been made only once
        expect(requestCount).toBe(1);
      },
      { timeout: 1000 },
    );

    server.close();
  });
});

describe("NewRequest - On-call request", () => {
  const developerStatus = {
    email: "dev@example.com",
    fullName: "Dev User",
    id: "user-1",
    status: "User is authenticated",
    licenseValid: false,
    permissions: ["datasource_connection:get", "execution_request:edit"],
    canManageOncall: false,
  } as StatusResponse;

  test("lets a normal user request on-call for themselves", async () => {
    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json(mockConnections, { status: 200 });
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
    );
    server.listen();

    render(
      <MemoryRouter>
        <UserStatusContext.Provider
          value={{
            userStatus: developerStatus,
            refreshState: async () => {},
            hasPermission: () => true,
          }}
        >
          <ConnectionChooser />
        </UserStatusContext.Provider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("request-oncall-self")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId("request-oncall-self"));
    expect(
      screen.getByText(/This request is only for you/),
    ).toBeInTheDocument();

    server.close();
  });

  test("also shows the self-request panel for managers", async () => {
    const server = setupServer(
      http.get("http://localhost:8081/connections/", () => {
        return HttpResponse.json(mockConnections, { status: 200 });
      }),
      http.get("http://localhost:8081/kubernetes/pods", () => {
        return HttpResponse.json({ pods: [] }, { status: 200 });
      }),
    );
    server.listen();

    render(
      <MemoryRouter>
        <UserStatusContext.Provider
          value={{
            userStatus: { ...developerStatus, canManageOncall: true },
            refreshState: async () => {},
            hasPermission: () => true,
          }}
        >
          <ConnectionChooser />
        </UserStatusContext.Provider>
      </MemoryRouter>,
    );

    await waitFor(() => {
      expect(screen.getByTestId("request-oncall-self")).toBeInTheDocument();
    });

    server.close();
  });
});
