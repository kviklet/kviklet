import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { rest } from "msw";
import { setupServer } from "msw/node";
import ConnectionChooser from "./NewRequest";

// Basic smoke tests for the NewRequest component
describe("NewRequest - Basic functionality", () => {
  test("renders the new request page", async () => {
    // Mock an empty connections response to avoid complex setup
    const server = setupServer(
      rest.get("http://localhost:8081/connections/", (req, res, ctx) => {
        return res(ctx.status(200), ctx.json([]));
      }),
      rest.get("http://localhost:8081/kubernetes/pods", (req, res, ctx) => {
        return res(ctx.status(200), ctx.json({ pods: [] }));
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
