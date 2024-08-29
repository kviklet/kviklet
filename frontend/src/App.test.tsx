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
  "http://localhost:8081/status",
  (req, res, ctx) => {
    return res(ctx.status(401));
  },
);

const handleStatusLoggedIn = rest.get(
  "http://localhost:8081/status",
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
  "http://localhost:8081/login",
  (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        sessionId: "test",
      }),
    );
  },
);

const handleGetExecutionRequests = rest.get(
  "http://localhost:8081/execution-requests/",
  (req, res, ctx) => {
    return res(ctx.status(200), ctx.json([]));
  },
);

const handleConfig = rest.get(
  "http://localhost:8081/config/",
  (req, res, ctx) => {
    return res(
      ctx.status(200),
      ctx.json({
        oauthProvider: "GOOGLE",
      }),
    );
  },
);

const server = setupServer(
  handleStatusNotLoggedIn,
  handleLogin,
  handleGetExecutionRequests,
  handleConfig,
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
    const element = await screen.findByText("Sign in to Kviklet");
    expect(element).toBeInTheDocument();
  });

  test("redirects to login", async () => {
    render(
      <MemoryRouter initialEntries={["/settings"]}>
        <App></App>
      </MemoryRouter>,
    );
    const element = await screen.findByText("Sign in to Kviklet");
    expect(element).toBeInTheDocument();
  });

  test("login works", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <App />
      </MemoryRouter>,
    );

    const element = await screen.findByText("Sign in to Kviklet");
    expect(element).toBeInTheDocument();

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
      const element = screen.queryByText("Sign in to Kviklet");
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
    const element = screen.queryByText("Sign in to Kviklet");
    expect(element).not.toBeInTheDocument();
  });

  test("renders settings page", () => {
    render(
      <MemoryRouter initialEntries={["/settings"]}>
        <App></App>
      </MemoryRouter>,
    );
    const element = screen.queryByText("Sign in to Kviklet");
    expect(element).not.toBeInTheDocument();
  });
});
