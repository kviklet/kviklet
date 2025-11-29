import { render, screen, waitFor, fireEvent } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import ConnectionChooser from "./NewRequest";

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
    roleArn: null,
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
    roleArn: null,
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
