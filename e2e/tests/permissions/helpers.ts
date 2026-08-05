import { APIRequestContext, Page, request } from "@playwright/test";

/**
 * Helpers for the issue-#482 permission-UI tests: create restricted roles/users
 * through the admin API and log in as them in the browser.
 *
 * State-changing requests must send the X-Kviklet-Request header (CSRF protection,
 * see CsrfHeaderFilter.kt). /login is exempt.
 */

// 127.0.0.1 rather than localhost: Node may resolve localhost to ::1, where the
// docker-compose port mapping does not listen. The container's nginx serves the
// backend under /api/ — leading-slash paths would drop a base path, so every
// request uses the explicit prefix.
const BASE = "http://127.0.0.1:4444";
const API = "/api";
const HEADERS = { "X-Kviklet-Request": "e2e" };

export const ADMIN = { email: "admin@admin.com", password: "admin" };

export async function apiLogin(
  email: string,
  password: string,
): Promise<APIRequestContext> {
  const ctx = await request.newContext({ baseURL: BASE });
  const res = await ctx.post(`${API}/login`, { data: { email, password } });
  if (!res.ok()) {
    throw new Error(`login as ${email} failed: ${res.status()}`);
  }
  return ctx;
}

export async function adminApi(): Promise<APIRequestContext> {
  return apiLogin(ADMIN.email, ADMIN.password);
}

export interface Policy {
  action: string;
  resource: string;
}

export async function createRole(
  api: APIRequestContext,
  name: string,
  policies: Policy[],
): Promise<string> {
  const res = await api.post(`${API}/roles/`, {
    headers: HEADERS,
    data: {
      name,
      description: `e2e permission test role ${name}`,
      policies: policies.map((p) => ({ id: null, ...p })),
    },
  });
  if (!res.ok()) {
    throw new Error(`createRole ${name} failed: ${res.status()} ${await res.text()}`);
  }
  const json = await res.json();
  return json.id;
}

export async function createUser(
  api: APIRequestContext,
  email: string,
  password: string,
  fullName: string,
): Promise<string> {
  const res = await api.post(`${API}/users/`, {
    headers: HEADERS,
    data: { email, password, fullName },
  });
  if (!res.ok()) {
    throw new Error(`createUser ${email} failed: ${res.status()} ${await res.text()}`);
  }
  const json = await res.json();
  return json.id;
}

export async function setUserRoles(
  api: APIRequestContext,
  userId: string,
  roleIds: string[],
): Promise<void> {
  const res = await api.patch(`${API}/users/${userId}`, {
    headers: HEADERS,
    data: { roles: roleIds },
  });
  if (!res.ok()) {
    throw new Error(`setUserRoles failed: ${res.status()} ${await res.text()}`);
  }
}

/** Fetch all roles (to find the default role id, which must stay assigned). */
export async function getRoles(api: APIRequestContext): Promise<
  { id: string; name: string; isDefault: boolean }[]
> {
  const res = await api.get(`${API}/roles/`);
  const json = await res.json();
  return json.roles;
}

/** Creates a Postgres connection against the e2e postgres container. */
export async function createConnection(
  api: APIRequestContext,
  id: string,
  displayName: string,
  extras: Record<string, unknown> = {},
): Promise<string> {
  const res = await api.post(`${API}/connections/`, {
    headers: HEADERS,
    data: {
      connectionType: "DATASOURCE",
      id,
      displayName,
      username: "postgres",
      password: "postgres",
      description: `e2e permission test connection ${id}`,
      reviewConfig: { numTotalRequired: 1 },
      type: "POSTGRESQL",
      protocol: "POSTGRESQL",
      hostname: "postgres",
      port: 5432,
      databaseName: "postgres",
      ...extras,
    },
  });
  if (!res.ok()) {
    throw new Error(`createConnection ${id} failed: ${res.status()} ${await res.text()}`);
  }
  const json = await res.json();
  return json.id;
}

/** Creates an execution request as the given (already logged-in) api context. */
export async function createRequest(
  api: APIRequestContext,
  connectionId: string,
  title: string,
  options: Record<string, unknown> = {},
): Promise<string> {
  const res = await api.post(`${API}/execution-requests/`, {
    headers: HEADERS,
    data: {
      connectionId,
      title,
      type: "SingleExecution",
      description: "e2e permission test request",
      statement: "SELECT 1;",
      connectionType: "DATASOURCE",
      ...options,
    },
  });
  if (!res.ok()) {
    throw new Error(`createRequest failed: ${res.status()} ${await res.text()}`);
  }
  const json = await res.json();
  return json.id;
}

export async function approveRequest(
  api: APIRequestContext,
  requestId: string,
): Promise<void> {
  const res = await api.post(`${API}/execution-requests/${requestId}/reviews`, {
    headers: HEADERS,
    data: { comment: "lgtm", action: "APPROVE" },
  });
  if (!res.ok()) {
    throw new Error(`approveRequest failed: ${res.status()} ${await res.text()}`);
  }
}

/** Browser login through the UI. */
export async function uiLogin(page: Page, email: string, password: string) {
  await page.goto("/");
  await page.waitForURL(/login/);
  await page.getByTestId("email-input").fill(email);
  await page.getByTestId("password-input").fill(password);
  await page.getByTestId("login-button").click();
}

/**
 * Convenience: create a user holding exactly the given policies (plus the
 * non-removable default role) and return their credentials.
 */
export async function createRestrictedUser(
  api: APIRequestContext,
  slug: string,
  policies: Policy[],
): Promise<{ email: string; password: string; userId: string }> {
  const email = `${slug}@e2e.test`;
  const password = "e2e-password-1";
  const userId = await createUser(api, email, password, `E2E ${slug}`);
  if (policies.length > 0) {
    const roleId = await createRole(api, `e2e-${slug}`, policies);
    const roles = await getRoles(api);
    const defaultRole = roles.find((r) => r.isDefault);
    await setUserRoles(
      api,
      userId,
      defaultRole ? [defaultRole.id, roleId] : [roleId],
    );
  }
  return { email, password, userId };
}
