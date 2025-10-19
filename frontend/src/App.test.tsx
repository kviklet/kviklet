import React from "react";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import App from "./App";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { MemoryRouter } from "react-router-dom";
import userEvent from "@testing-library/user-event";

const handleStatusNotLoggedIn = http.get("http://localhost:8081/status", () => {
  return new HttpResponse(null, { status: 401 });
});

const handleStatusLoggedIn = http.get("http://localhost:8081/status", () => {
  return HttpResponse.json(
    {
      email: "testUser@example.com",
      fullName: "Admin User",
      id: "qJ2HUad7BVFCtqQpWjzQpM",
      status: "User is authenticated",
    },
    { status: 200 },
  );
});
const handleLogin = http.post("http://localhost:8081/login", () => {
  return HttpResponse.json(
    {
      sessionId: "test",
    },
    { status: 200 },
  );
});

const handleGetExecutionRequests = http.get(
  "http://localhost:8081/execution-requests/",
  () => {
    return HttpResponse.json(
      {
        requests: [],
        hasMore: false,
        cursor: null,
      },
      { status: 200 },
    );
  },
);

const handleConfig = http.get("http://localhost:8081/config/", () => {
  return HttpResponse.json(
    {
      licenseValid: false,
      oauthProvider: "GOOGLE",
      ldapEnabled: false,
      samlEnabled: false,
    },
    { status: 200 },
  );
});

const server = setupServer(
  handleStatusNotLoggedIn,
  handleLogin,
  handleGetExecutionRequests,
  handleConfig,
);

beforeAll(() => {
  server.listen();
  // Mock IntersectionObserver
  global.IntersectionObserver = class IntersectionObserver {
    constructor() {}
    disconnect() {}
    observe() {}
    takeRecords() {
      return [];
    }
    unobserve() {}
  } as unknown as typeof IntersectionObserver;
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
