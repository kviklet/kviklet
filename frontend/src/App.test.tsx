import React from "react";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import App from "./App";
import { rest } from "msw";
import { setupServer } from "msw/node";
import { MemoryRouter } from "react-router-dom";
import userEvent from "@testing-library/user-event";

const handleStatusNotLoggedIn = rest.get(
  "http://localhost:8080/status",
  (req, res, ctx) => {
    return res(ctx.status(401));
  },
);

const handleStatusLoggedIn = rest.get(
  "http://localhost:8080/status",
  (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        email: "testUser@example.com",
        fullName: "Admin User",
        id: "qJ2HUad7BVFCtqQpWjzQpM",
        status: "User is authenticated",
      }),
    );
  },
);
const handleLogin = rest.post(
  "http://localhost:8080/login",
  (req, res, ctx) => {
    return res(ctx.status(200));
  },
);

const handleGetExecutionRequests = rest.get(
  "http://localhost:8080/execution-requests/",
  (req, res, ctx) => {
    return res(ctx.status(200), ctx.json([]));
  },
);
const server = setupServer(
  handleStatusNotLoggedIn,
  handleLogin,
  handleGetExecutionRequests,
);

beforeAll(() => {
  server.listen();
});
afterAll(() => server.close());
afterEach(() => server.resetHandlers());

describe("App not logged in", () => {
  test("renders sign in text", async () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );
    const element = await screen.findByText("Sign in to OpsGate");
    expect(element).toBeInTheDocument();
  });

  test("redirects to login", async () => {
    render(
      <MemoryRouter initialEntries={["/settings"]}>
        <App></App>
      </MemoryRouter>,
    );
    const element = await screen.findByText("Sign in to OpsGate");
    expect(element).toBeInTheDocument();
  });

  test("login works", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <App />
      </MemoryRouter>,
    );
    expect(screen.queryByText("Sign in to OpsGate")).toBeInTheDocument();
    server.use(handleStatusLoggedIn);
    const emailInput = screen.getByLabelText("Email");
    userEvent.type(emailInput, "some@email.com");
    const passwordInput = screen.getByLabelText("Password");
    userEvent.type(passwordInput, "somePassword");
    const loginButton = screen.getByRole("button", { name: "Sign in" });
    act(() => {
      fireEvent.click(loginButton);
    });

    await waitFor(() => {
      const element = screen.queryByText("Sign in to OpsGate");
      expect(element).not.toBeInTheDocument();
    });
  });
});

describe("App logged in", () => {
  beforeAll(() => {
    server.use(handleStatusLoggedIn);
  });

  afterAll(() => {
    server.resetHandlers();
  });

  test("renders requests page", () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    );
    const element = screen.queryByText("Sign in to OpsGate");
    expect(element).not.toBeInTheDocument();
  });

  test("renders settings page", () => {
    render(
      <MemoryRouter initialEntries={["/settings"]}>
        <App></App>
      </MemoryRouter>,
    );
    const element = screen.queryByText("Sign in to OpsGate");
    expect(element).not.toBeInTheDocument();
  });
});
