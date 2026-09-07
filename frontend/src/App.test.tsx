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
import { MemoryRouter, useLocation } from "react-router-dom";
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
      licenseValid: false,
      permissions: ["execution_request:get"],
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

const handleLogout = http.post("http://localhost:8081/logout", () => {
  return new HttpResponse(null, { status: 200 });
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
      oauthProvider: "google",
      ldapEnabled: false,
      samlEnabled: false,
      version: "test",
      buildDate: "2020-01-01",
      gitCommit: "abc",
    },
    { status: 200 },
  );
});

const server = setupServer(
  handleStatusNotLoggedIn,
  handleLogin,
  handleLogout,
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

// Renders the current router location so tests can assert where the app navigated to.
const LocationDisplay = () => {
  const location = useLocation();
  return (
    <div data-testid="location">
      {location.pathname + location.search + location.hash}
    </div>
  );
};

// The config mock enables Google SSO, so the email form is behind a toggle.
const signIn = () => {
  const showEmailForm = screen.queryByRole("button", {
    name: "Sign in with email instead",
  });
  if (showEmailForm) {
    userEvent.click(showEmailForm);
  }
  const emailInput = screen.getByLabelText("Email");
  userEvent.type(emailInput, "some@email.com");
  const passwordInput = screen.getByLabelText("Password");
  userEvent.type(passwordInput, "somePassword");
  const loginButton = screen.getByRole("button", { name: "Sign in" });
  act(() => {
    fireEvent.click(loginButton);
  });
};

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
    signIn();

    await waitFor(() => {
      const element = screen.queryByText("Sign in to Kviklet");
      expect(element).not.toBeInTheDocument();
    });
  });

  test("remembers the requested page in the login url", async () => {
    render(
      <MemoryRouter initialEntries={["/settings/profile?tab=1#top"]}>
        <App />
        <LocationDisplay />
      </MemoryRouter>,
    );
    await screen.findByText("Sign in to Kviklet");
    expect(screen.getByTestId("location")).toHaveTextContent(
      "/login?redirect=%2Fsettings%2Fprofile%3Ftab%3D1%23top",
    );
  });

  test("passes the requested page on to the SSO login", async () => {
    render(
      <MemoryRouter initialEntries={["/login?redirect=%2Fsettings%2Fusers"]}>
        <App />
      </MemoryRouter>,
    );
    await screen.findByText("Sign in to Kviklet");
    const ssoLink = screen
      .getAllByRole("link")
      .find((link) => link.getAttribute("href")?.includes("/oauth2/"));
    expect(ssoLink).toHaveAttribute(
      "href",
      "http://localhost:8081/oauth2/authorization/google?redirect=%2Fsettings%2Fusers",
    );
  });

  test("returns to the requested page after login", async () => {
    render(
      <MemoryRouter initialEntries={["/settings/profile"]}>
        <App />
        <LocationDisplay />
      </MemoryRouter>,
    );
    await screen.findByText("Sign in to Kviklet");

    server.use(handleStatusLoggedIn);
    signIn();

    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent(
        "/settings/profile",
      );
    });
    expect(screen.queryByText("Sign in to Kviklet")).not.toBeInTheDocument();
  });

  test("ignores redirect targets that leave the site", async () => {
    render(
      <MemoryRouter
        initialEntries={[
          "/login?redirect=" + encodeURIComponent("//evil.example"),
        ]}
      >
        <App />
        <LocationDisplay />
      </MemoryRouter>,
    );
    await screen.findByText("Sign in to Kviklet");

    server.use(handleStatusLoggedIn);
    signIn();

    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent(/^\/$/);
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

  test("logout lands on a plain login page", async () => {
    server.use(handleStatusLoggedIn);
    render(
      <MemoryRouter initialEntries={["/settings/profile"]}>
        <App />
        <LocationDisplay />
      </MemoryRouter>,
    );
    const dropdown = await screen.findByTestId("settings-dropdown");
    userEvent.click(dropdown);
    const logoutButton = await screen.findByRole("button", { name: "Logout" });

    server.use(handleStatusNotLoggedIn);
    userEvent.click(logoutButton);

    await screen.findByText("Sign in to Kviklet");
    expect(screen.getByTestId("location")).toHaveTextContent(/^\/login$/);
  });

  test("login page forwards to the requested page when already logged in", async () => {
    // The top-level afterEach resets the handlers, so the describe's beforeAll only
    // covers its first test.
    server.use(handleStatusLoggedIn);
    render(
      <MemoryRouter initialEntries={["/login?redirect=%2Fsettings%2Fprofile"]}>
        <App />
        <LocationDisplay />
      </MemoryRouter>,
    );
    await waitFor(() => {
      expect(screen.getByTestId("location")).toHaveTextContent(
        "/settings/profile",
      );
    });
  });
});
