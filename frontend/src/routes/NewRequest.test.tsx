import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { http, HttpResponse } from "msw";
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
