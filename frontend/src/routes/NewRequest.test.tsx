import React from "react";
import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { http, HttpResponse, delay } from "msw";
import { setupServer } from "msw/node";
import ConnectionChooser from "./NewRequest";

// Basic smoke tests for the NewRequest component
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

    // Check that the page title is rendered
    expect(
      screen.getByText("Request Access to a Database"),
    ).toBeInTheDocument();

    // Wait for loading to complete and connections section to appear
    await waitFor(() => {
      expect(screen.getByText("Connections")).toBeInTheDocument();
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
              roleArn: null,
              maxTemporaryAccessDuration: null,
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
