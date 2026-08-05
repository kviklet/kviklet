import { test, expect, Page, APIRequestContext } from "@playwright/test";
import {
  adminApi,
  createConnection,
  createRestrictedUser,
  createRole,
  createUser,
  getRoles,
  setUserRoles,
  uiLogin,
  ADMIN,
} from "./helpers";

/**
 * Validation spec for findings T1-02, T1-17, T2-05, T2-06, T2-07, T2-08, T2-19
 * and manifest item NR-2 (issue #482 permission-aware UI).
 *
 * All checks drive the real app at localhost:4444. Two checks need a state this
 * stack cannot produce through real data and use response interception instead:
 *  - T1-02 negative: the mandatory default role always grants
 *    datasource_connection:get (the backend rejects removing it from a user), so
 *    the "no get anywhere" state is simulated by stripping that permission from
 *    the real /api/status response.
 *  - T2-08: the role-requirements section only shows its permission-driven states
 *    with a valid enterprise license; the stack has none, so licenseValid is
 *    flipped to true on the real /api/config/ response. Everything else
 *    (role:get gating, fetch suppression) is real app behavior.
 */

const NO_REQUEST_TOOLTIP =
  "You lack permission to create requests on this connection";
const NO_EDIT_TOOLTIP = "You lack permission to edit this connection.";

const ts = Date.now();
const connAId = `setb-a-${ts}`;
const connAName = `SetB ConnA ${ts}`;
const connBId = `setb-b-${ts}`;
const connBName = `SetB ConnB ${ts}`;
const connKId = `setb-k8s-${ts}`;
const connKName = `SetB K8s ${ts}`;

let viewer: { email: string; password: string };
let scoped: { email: string; password: string };

const HEADERS = { "X-Kviklet-Request": "e2e" };

async function createKubernetesConnection(
  api: APIRequestContext,
  id: string,
  displayName: string,
): Promise<void> {
  const res = await api.post(`/api/connections/`, {
    headers: HEADERS,
    data: {
      connectionType: "KUBERNETES",
      id,
      displayName,
      description: `e2e permission test k8s connection ${id}`,
      reviewConfig: { numTotalRequired: 1 },
    },
  });
  if (!res.ok()) {
    throw new Error(
      `createKubernetesConnection ${id} failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** Log in through the UI and wait until the app has left the login page. */
async function loginAndGo(
  page: Page,
  user: { email: string; password: string },
  path: string,
) {
  await uiLogin(page, user.email, user.password);
  await page.waitForURL((url) => !url.pathname.includes("login"));
  await page.goto(path);
}

test.beforeEach(() => {
  test.setTimeout(30000);
});

test.beforeAll(async () => {
  test.setTimeout(60000);
  const api = await adminApi();

  await createConnection(api, connAId, connAName, {
    temporaryAccessEnabled: true,
    dumpsEnabled: true,
  });
  await createConnection(api, connBId, connBName);
  await createKubernetesConnection(api, connKId, connKName);

  // Default role only: datasource_connection:get / execution_request:get /
  // user:get on * — no edit, create or role:get anywhere.
  viewer = await createRestrictedUser(api, `setb-viewer-${ts}`, []);

  // execution_request:edit and datasource_connection:edit scoped to conn A only.
  const roleId = await createRole(api, `setb-scoped-${ts}`, [
    { action: "execution_request:edit", resource: connAId },
    { action: "datasource_connection:edit", resource: connAId },
  ]);
  const email = `setb-scoped-${ts}@e2e.test`;
  const password = "e2e-password-1";
  const userId = await createUser(api, email, password, `E2E setb-scoped-${ts}`);
  const roles = await getRoles(api);
  const defaultRole = roles.find((r) => r.isDefault);
  await setUserRoles(
    api,
    userId,
    defaultRole ? [defaultRole.id, roleId] : [roleId],
  );
  scoped = { email, password };
});

// ---------------------------------------------------------------------------
// T1-02 — "New" nav link gated on datasource_connection:get
// ---------------------------------------------------------------------------

test("T1-02 positive: user holding datasource_connection:get sees the New nav link", async ({
  page,
}) => {
  await loginAndGo(page, viewer, "/requests");
  await expect(page.getByTestId("requests-link")).toBeVisible();
  await expect(page.getByTestId("new-link")).toBeVisible();
});

test("T1-02 negative: without datasource_connection:get anywhere the New nav link is hidden", async ({
  page,
}) => {
  // The backend refuses to detach the mandatory default role (which grants
  // datasource_connection:get on *), so simulate the deployment where that role
  // was edited: strip the permission from the otherwise-real status response.
  await page.route("**/api/status", async (route) => {
    const resp = await route.fetch();
    let body = await resp.text();
    try {
      const json = JSON.parse(body) as { permissions?: string[] };
      if (Array.isArray(json.permissions)) {
        json.permissions = json.permissions.filter(
          (p) => p !== "datasource_connection:get",
        );
        body = JSON.stringify(json);
      }
    } catch {
      // non-JSON (e.g. logged-out) — pass through unchanged
    }
    await route.fulfill({ response: resp, body });
  });

  await loginAndGo(page, viewer, "/requests");
  // Requests link visible proves the nav rendered with a loaded user status.
  await expect(page.getByTestId("requests-link")).toBeVisible();
  await expect(page.getByTestId("new-link")).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// T1-17 + NR-2 — add-connection entry points gated on datasource_connection:create
// ---------------------------------------------------------------------------

test("T1-17 positive: admin sees add-connection buttons on /settings/connections", async ({
  page,
}) => {
  await loginAndGo(page, ADMIN, "/settings/connections");
  await expect(page.getByTestId("connections-table")).toBeVisible();
  expect(
    await page.getByTestId("connections-table-create-button").count(),
  ).toBeGreaterThan(0);
});

test("T1-17 negative + NR-2: without datasource_connection:create no create entry point (modal unreachable)", async ({
  page,
}) => {
  await loginAndGo(page, viewer, "/settings/connections");
  await expect(page.getByTestId("connections-table")).toBeVisible();
  // The connections themselves are visible (viewer holds get on *)...
  await expect(
    page.getByTestId(`connections-table-row-${connAId}`),
  ).toBeVisible();
  // ...but every create entry point (per-category "+" and empty-state button
  // share the testid) is gone, so the create modal cannot be reached (NR-2).
  await expect(page.getByTestId("connections-table-create-button")).toHaveCount(
    0,
  );
  await expect(page.getByTestId("add-database-connection-button")).toHaveCount(
    0,
  );
  await expect(page.getByText("Choose Connection Type")).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// T2-19 — per-connection Query/Access/DB Dump buttons on /new gated on
// object-scoped execution_request:edit
// ---------------------------------------------------------------------------

test("T2-19: /new buttons enabled on conn A (scoped edit) and disabled with tooltip on conn B", async ({
  page,
}) => {
  await loginAndGo(page, scoped, "/new");

  const queryA = page.getByTestId(`query-button-${connAName}`);
  const accessA = page.getByTestId(`access-button-${connAName}`);
  const queryB = page.getByTestId(`query-button-${connBName}`);

  await expect(queryA).toBeEnabled();
  expect(await queryA.getAttribute("title")).toBeNull();
  await expect(accessA).toBeEnabled();

  await expect(queryB).toBeDisabled();
  await expect(queryB).toHaveAttribute("title", NO_REQUEST_TOOLTIP);

  // Positive path really works: clicking Query on conn A opens the request form.
  await queryA.click();
  await expect(page.getByTestId("request-title")).toBeVisible();
});

test("T2-19 negative: user without execution_request:edit anywhere gets all /new buttons disabled with tooltip", async ({
  page,
}) => {
  await loginAndGo(page, viewer, "/new");

  const queryA = page.getByTestId(`query-button-${connAName}`);
  const accessA = page.getByTestId(`access-button-${connAName}`);
  await expect(queryA).toBeDisabled();
  await expect(queryA).toHaveAttribute("title", NO_REQUEST_TOOLTIP);
  await expect(accessA).toBeDisabled();
  await expect(accessA).toHaveAttribute("title", NO_REQUEST_TOOLTIP);

  // DB Dump button (no testid) inside conn A's card is disabled too.
  const cardA = queryA.locator("xpath=ancestor::li[1]");
  const dumpA = cardA.getByRole("button", { name: "DB Dump" });
  await expect(dumpA).toBeDisabled();
  await expect(dumpA).toHaveAttribute("title", NO_REQUEST_TOOLTIP);

  const queryB = page.getByTestId(`query-button-${connBName}`);
  await expect(queryB).toBeDisabled();
});

// ---------------------------------------------------------------------------
// T2-05 — datasource details Save gated on object-scoped datasource_connection:edit
// ---------------------------------------------------------------------------

test("T2-05 positive: scoped editor can save conn A details (Save enables when dirty, no tooltip)", async ({
  page,
}) => {
  await loginAndGo(page, scoped, `/settings/connections/${connAId}`);
  const save = page.getByRole("button", { name: "Save", exact: true });
  await expect(save).toBeVisible();
  // Pristine form: Save disabled only because nothing changed yet.
  await page.locator("#displayName").fill(`${connAName} edited`);
  await expect(save).toBeEnabled();
  expect(await save.getAttribute("title")).toBeNull();
});

test("T2-05 negative: Save on conn B stays disabled with permission tooltip for the scoped editor", async ({
  page,
}) => {
  await loginAndGo(page, scoped, `/settings/connections/${connBId}`);
  const save = page.getByRole("button", { name: "Save", exact: true });
  await expect(save).toBeVisible();
  await expect(save).toHaveAttribute("title", NO_EDIT_TOOLTIP);
  // Even a dirty form must not enable Save without edit permission.
  await page.locator("#displayName").fill(`${connBName} edited`);
  await expect(save).toBeDisabled();
  await expect(save).toHaveAttribute("title", NO_EDIT_TOOLTIP);
});

// ---------------------------------------------------------------------------
// T2-06 — kubernetes details Save gated on object-scoped datasource_connection:edit
// ---------------------------------------------------------------------------

test("T2-06 positive: admin can save k8s connection details (Save enables when dirty)", async ({
  page,
}) => {
  await loginAndGo(page, ADMIN, `/settings/connections/${connKId}`);
  const save = page.getByRole("button", { name: "Save", exact: true });
  await expect(save).toBeVisible();
  await page
    .getByTestId("kubernetes-connection-name")
    .fill(`${connKName} edited`);
  await expect(save).toBeEnabled();
  expect(await save.getAttribute("title")).toBeNull();
});

test("T2-06 negative: k8s Save disabled with tooltip for user without datasource_connection:edit", async ({
  page,
}) => {
  await loginAndGo(page, viewer, `/settings/connections/${connKId}`);
  const save = page.getByRole("button", { name: "Save", exact: true });
  await expect(save).toBeVisible();
  await expect(save).toHaveAttribute("title", NO_EDIT_TOOLTIP);
  await page
    .getByTestId("kubernetes-connection-name")
    .fill(`${connKName} edited`);
  await expect(save).toBeDisabled();
});

// ---------------------------------------------------------------------------
// T2-07 — Delete button gated on object-scoped datasource_connection:edit
// ---------------------------------------------------------------------------

test("T2-07 positive: scoped editor gets an enabled Delete on conn A", async ({
  page,
}) => {
  await loginAndGo(page, scoped, `/settings/connections/${connAId}`);
  const del = page.getByRole("button", { name: "Delete", exact: true });
  await expect(del).toBeVisible();
  await expect(del).toBeEnabled();
  expect(await del.getAttribute("title")).toBeNull();
  // Do not click — the connection is shared setup for other tests.
});

test("T2-07 negative: Delete disabled with tooltip without edit permission on the connection", async ({
  page,
}) => {
  await loginAndGo(page, viewer, `/settings/connections/${connAId}`);
  const del = page.getByRole("button", { name: "Delete", exact: true });
  await expect(del).toBeVisible();
  await expect(del).toBeDisabled();
  await expect(del).toHaveAttribute("title", NO_EDIT_TOOLTIP);
  // Disabled button must not open the confirm modal.
  await del.click({ force: true });
  await expect(page.getByText("Delete connection")).toHaveCount(0);
});

test("T2-07 negative: scoped editor's Delete on conn B is disabled with tooltip", async ({
  page,
}) => {
  await loginAndGo(page, scoped, `/settings/connections/${connBId}`);
  const delB = page.getByRole("button", { name: "Delete", exact: true });
  await expect(delB).toBeVisible();
  await expect(delB).toBeDisabled();
  await expect(delB).toHaveAttribute("title", NO_EDIT_TOOLTIP);
});

// ---------------------------------------------------------------------------
// T2-08 — role-requirements dropdown gated on role:get (explains instead of
// silently empty). Needs licenseValid=true, injected onto the real /config/.
// ---------------------------------------------------------------------------

async function mockValidLicense(page: Page) {
  await page.route("**/api/config/", async (route) => {
    if (route.request().method() !== "GET") {
      await route.fallback();
      return;
    }
    const resp = await route.fetch();
    let body = await resp.text();
    try {
      const json = JSON.parse(body) as { licenseValid?: boolean };
      json.licenseValid = true;
      body = JSON.stringify(json);
    } catch {
      // pass through
    }
    await route.fulfill({ response: resp, body });
  });
}

test("T2-08 positive: with role:get (admin) the role-requirements editor is available", async ({
  page,
}) => {
  await mockValidLicense(page);
  await loginAndGo(page, ADMIN, `/settings/connections/${connAId}`);
  await expect(
    page.getByRole("heading", { name: "Role-Specific Requirements" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Add Role Requirement" }),
  ).toBeVisible();
  await expect(
    page.getByText("You lack permission to list roles"),
  ).toHaveCount(0);
});

test("T2-08 negative: without role:get the section explains itself, hides the editor and skips the roles fetch", async ({
  page,
}) => {
  await mockValidLicense(page);
  let rolesFetched = false;
  page.on("request", (req) => {
    if (req.url().includes("/api/roles")) {
      rolesFetched = true;
    }
  });

  await loginAndGo(page, viewer, `/settings/connections/${connAId}`);
  await expect(
    page.getByRole("heading", { name: "Role-Specific Requirements" }),
  ).toBeVisible();
  await expect(
    page.getByText(
      "You lack permission to list roles, so role-specific requirements cannot be configured here.",
    ),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Add Role Requirement" }),
  ).toHaveCount(0);
  expect(rolesFetched).toBe(false);
});
