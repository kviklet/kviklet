import { test, expect, Page } from "@playwright/test";
import {
  ADMIN,
  adminApi,
  createRestrictedUser,
  getRoles,
  uiLogin,
} from "./helpers";

/**
 * Validation spec for issue-#482 findings (set A):
 * T1-04..T1-16 (settings/nav/users/roles/config/license), T2-01..T2-04, T3-A.
 *
 * Notes on reachability:
 * - The mandatory default role always grants datasource_connection:get,
 *   execution_request:get and user:get, so no real user can lack them. For
 *   T1-07 (Connections tab hidden) and the T3-A fetch-403 path, the /api/status
 *   response is stubbed to produce the otherwise unreachable permission state,
 *   while the rest of the app (and for T3-A the real 403 from /api/roles/)
 *   stays live.
 * - The instance has no enterprise license: the Role Sync and API Keys pages
 *   render their license lock for everyone, so the permission-specific control
 *   states behind them (T2-02, T2-03) are not reachable; only tab visibility
 *   and the locked page are asserted.
 */

const RUN = Date.now();

interface Creds {
  email: string;
  password: string;
  userId: string;
}

// default role only: datasource_connection:get, execution_request:get, user:get
let baseUser: Creds;
// default role + role:get
let roleViewer: Creds;
// default role + configuration:get
let configViewer: Creds;
let defaultRoleId: string;

test.beforeAll(async () => {
  test.setTimeout(60000);
  const api = await adminApi();
  baseUser = await createRestrictedUser(api, `setA-base-${RUN}`, []);
  roleViewer = await createRestrictedUser(api, `setA-roleview-${RUN}`, [
    { action: "role:get", resource: "*" },
  ]);
  configViewer = await createRestrictedUser(api, `setA-configview-${RUN}`, [
    { action: "configuration:get", resource: "*" },
  ]);
  const roles = await getRoles(api);
  const defaultRole = roles.find((r) => r.isDefault);
  if (!defaultRole) throw new Error("no default role found");
  defaultRoleId = defaultRole.id;
});

async function loginAs(page: Page, creds: { email: string; password: string }) {
  await uiLogin(page, creds.email, creds.password);
  // All users in this spec hold execution_request:get, so the index shows requests.
  await page.waitForURL((url) => !url.pathname.includes("login"));
}

test("T1-04/T1-05/T1-06/T1-08 + T2-04: default-only user sees restricted settings sidebar and disabled profile save", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, baseUser);

  await page.goto("/settings");
  // T1-04: without configuration:get the settings landing redirects to Profile.
  await page.waitForURL("**/settings/profile");

  // Sidebar is rendered (positive control: tabs the default role grants).
  await expect(page.getByTestId("settings-profile")).toBeVisible();
  await expect(page.getByTestId("settings-users")).toBeVisible();
  // T1-07 positive half: default role grants datasource_connection:get -> tab shown.
  await expect(page.getByTestId("settings-connections")).toBeVisible();

  // T1-04: General tab hidden without configuration:get.
  await expect(page.getByTestId("settings-general")).toHaveCount(0);
  // T1-05: Roles tab hidden without role:get.
  await expect(page.getByTestId("settings-roles")).toHaveCount(0);
  // T1-06: Role Sync tab hidden without configuration:get.
  await expect(page.getByTestId("settings-role-sync")).toHaveCount(0);
  // T1-08: API Keys tab hidden without api_key:get.
  await expect(page.getByTestId("settings-api-keys")).toHaveCount(0);

  // T2-04: without user:edit the change-password form is hidden entirely; the
  // profile page shows account info plus an explanatory note instead.
  await expect(page.getByText(baseUser.email)).toBeVisible();
  await expect(
    page.getByText("Profile editing is not enabled for your role."),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Save" })).toHaveCount(0);
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
});

test("T1-16: license upload hidden without configuration:edit, page itself still readable", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, baseUser);

  await page.goto("/settings/license");
  // Page content loads (stats card), proving we are not on an error page.
  await expect(page.getByText("License Valid until")).toBeVisible();
  // Upload dropzone and Upload button hidden without configuration:edit.
  await expect(page.getByText("Click to upload")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Upload" })).toHaveCount(0);
});

test("positive controls: admin sees every tab, General landing, enabled forms, license upload", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, ADMIN);

  await page.goto("/settings");
  // Admin (configuration:get) stays on the General settings landing.
  await expect(page).toHaveURL(/\/settings$/);
  await expect(
    page.getByRole("heading", { name: "General Settings" }),
  ).toBeVisible();
  await expect(page.getByText("Notification Settings")).toBeVisible();

  await expect(page.getByTestId("settings-general")).toBeVisible();
  await expect(page.getByTestId("settings-roles")).toBeVisible();
  await expect(page.getByTestId("settings-connections")).toBeVisible();
  await expect(page.getByTestId("settings-users")).toBeVisible();

  // T1-06/T1-08: tabs visible for a permitted user, license-locked (tooltip kept).
  const roleSync = page.getByTestId("settings-role-sync");
  await expect(roleSync).toBeVisible();
  await expect(roleSync.locator("div[title]").first()).toHaveAttribute(
    "title",
    /enterprise/i,
  );
  const apiKeys = page.getByTestId("settings-api-keys");
  await expect(apiKeys).toBeVisible();
  await expect(apiKeys.locator("div[title]").first()).toHaveAttribute(
    "title",
    /enterprise/i,
  );

  // T1-15 positive: notification form editable for configuration:edit holder.
  await expect(
    page.getByText(
      "You can view these settings but lack the permission to change them.",
    ),
  ).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Save" })).toBeEnabled();

  // T1-16 positive: upload dropzone visible with configuration:edit.
  await page.goto("/settings/license");
  await expect(page.getByText("Click to upload")).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload" })).toBeVisible();
});

test("T1-07 + T3-A: stubbed status without datasource_connection:get hides Connections tab; server 403 on roles renders NotAuthorized", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, baseUser);

  // T3-A first part, no stubbing: without role:get the roles route is guarded.
  await page.goto("/settings/roles");
  await expect(page.getByTestId("not-authorized")).toBeVisible();
  await expect(page.getByTestId("roles-table")).toHaveCount(0);

  // Stub /api/status: drop datasource_connection:get (unreachable for real users,
  // the mandatory default role always grants it) and claim role:get so the client
  // guard passes while the live server still 403s the /roles/ fetch.
  await page.route("**/api/status", async (route) => {
    const response = await route.fetch();
    const json = (await response.json()) as { permissions: string[] };
    json.permissions = json.permissions
      .filter((p) => p !== "datasource_connection:get")
      .concat(["role:get"]);
    await route.fulfill({ response, json });
  });

  // T1-07: Connections tab hidden when the user holds no datasource_connection:get.
  await page.goto("/settings");
  await expect(page.getByTestId("settings-users")).toBeVisible();
  await expect(page.getByTestId("settings-connections")).toHaveCount(0);

  // T3-A: client believes role:get, real backend answers 403 -> NotAuthorized,
  // not a fake empty roles list.
  await page.goto("/settings/roles");
  await expect(page.getByTestId("not-authorized")).toBeVisible();
  await expect(page.getByTestId("roles-table")).toHaveCount(0);
});

test("T1-09/T1-10/T1-11: users page for default-only user (no add button, read-only roles, no /roles/ fetch)", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, baseUser);

  const roleRequests: string[] = [];
  page.on("request", (req) => {
    if (/\/api\/roles\/?(\?.*)?$/.test(req.url())) {
      roleRequests.push(req.url());
    }
  });

  await page.goto("/settings/users");
  // Wait for the user list to load (admin row is always present).
  await expect(page.getByTestId(`user-${ADMIN.email}`)).toBeVisible();

  // T1-09: Add User hidden without user:create.
  await expect(page.getByTestId("add-user-button")).toHaveCount(0);

  // T1-10: read-only role names instead of the combobox without user:edit_roles.
  await expect(page.getByTestId("role-combobox-button")).toHaveCount(0);
  const ownRow = page.getByTestId(`user-${baseUser.email}`);
  const readOnlyRoles = ownRow.locator(
    '[title="You lack permission to change user roles."]',
  );
  await expect(readOnlyRoles).toBeVisible();
  await expect(readOnlyRoles).toContainText("Default");

  // T1-11: no /roles/ fetch fired and no 403 toast shown.
  await expect(page.getByText("Failed to load Roles")).toHaveCount(0);
  expect(roleRequests).toEqual([]);
});

test("T1-09/T1-10 positive: admin sees Add User and the role combobox", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, ADMIN);

  await page.goto("/settings/users");
  await expect(page.getByTestId(`user-${ADMIN.email}`)).toBeVisible();
  await expect(page.getByTestId("add-user-button")).toBeVisible();
  expect(
    await page.getByTestId("role-combobox-button").count(),
  ).toBeGreaterThan(0);
});

test("T1-12/T1-13/T1-14/T2-01: role:get-only user gets read-only roles surfaces", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, roleViewer);

  await page.goto("/settings/roles");
  // Positive control: the roles list itself loads.
  await expect(page.getByTestId("settings-roles")).toBeVisible();
  await expect(page.getByTestId("roles-table")).toBeVisible();

  // T1-12: Add Role button hidden without role:edit.
  await expect(page.getByTestId("roles-table-create-button")).toHaveCount(0);
  // T1-13: no delete trash icons without role:edit.
  await expect(
    page.locator('[data-testid^="roles-table-delete-"]'),
  ).toHaveCount(0);

  // T1-14: the new-role route is guarded by role:edit.
  await page.goto("/settings/roles/new");
  await expect(page.getByTestId("not-authorized")).toBeVisible();

  // T2-01: role details form read-only with explanation for role:get-only user;
  // the Submit button is hidden entirely.
  await page.goto(`/settings/roles/${defaultRoleId}`);
  await expect(
    page.getByText(
      "You can view this role but lack the permission to change it.",
    ),
  ).toBeVisible();
  const nameInput = page.locator('input[name="name"]');
  await expect(nameInput).toBeVisible();
  await expect(nameInput).toBeDisabled();
  await expect(page.getByRole("button", { name: "Submit" })).toHaveCount(0);
});

test("T1-12/T1-13/T1-14/T2-01 positive + T1-14 failure surfacing: admin roles surfaces", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, ADMIN);

  await page.goto("/settings/roles");
  await expect(page.getByTestId("roles-table")).toBeVisible();
  // T1-12/T1-13 positive: create button and delete icons exist with role:edit.
  await expect(page.getByTestId("roles-table-create-button")).toBeVisible();
  expect(
    await page.locator('[data-testid^="roles-table-delete-"]').count(),
  ).toBeGreaterThan(0);

  // T2-01 positive: role details form editable with role:edit.
  await page.goto(`/settings/roles/${defaultRoleId}`);
  const nameInput = page.locator('input[name="name"]');
  await expect(nameInput).toBeVisible();
  await expect(nameInput).toBeEnabled();

  // T1-14: new-role form reachable with role:edit, and a failing submit surfaces
  // an error instead of false-success navigation. The POST is stubbed to 403 —
  // real submits either succeed or would pollute the shared instance.
  await page.route("**/api/roles/", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ message: "Access Denied" }),
      });
      return;
    }
    await route.fallback();
  });

  await page.goto("/settings/roles/new");
  await expect(page.getByTestId("not-authorized")).toHaveCount(0);
  await page.locator('input[name="name"]').fill(`setA-t114-${RUN}`);
  await page.locator('input[name="description"]').fill("t1-14 probe");
  await page.getByRole("button", { name: "Submit" }).click();

  await expect(page.getByText("Failed to create role")).toBeVisible();
  // No false-success navigation back to the roles list.
  await expect(page).toHaveURL(/\/settings\/roles\/new$/);
});

test("T1-15/T1-06/T2-02/T2-03: configuration:get-only user gets read-only general settings and locked role sync", async ({
  page,
}) => {
  test.setTimeout(30000);
  await loginAs(page, configViewer);

  await page.goto("/settings");
  // With configuration:get the General landing renders (no redirect to profile).
  await expect(page).toHaveURL(/\/settings$/);
  await expect(
    page.getByRole("heading", { name: "General Settings" }),
  ).toBeVisible();

  // T1-15: read-only note + disabled webhook form without configuration:edit;
  // the Save button is hidden entirely.
  await expect(
    page.getByText(
      "You can view these settings but lack the permission to change them.",
    ),
  ).toBeVisible();
  await expect(page.getByPlaceholder("Slack URL")).toBeDisabled();
  await expect(page.getByRole("button", { name: "Save" })).toHaveCount(0);

  // T1-06 positive half: Role Sync tab visible with configuration:get, license
  // lock (icon + tooltip) kept.
  const roleSync = page.getByTestId("settings-role-sync");
  await expect(roleSync).toBeVisible();
  await expect(roleSync.locator("div[title]").first()).toHaveAttribute(
    "title",
    /enterprise/i,
  );

  // T2-02/T2-03: without a license the role-sync controls never render; the
  // page shows the enterprise lock for this configuration:get-only user, so the
  // disable-with-tooltip states themselves are NOT testable on this instance.
  await page.goto("/settings/role-sync");
  await expect(
    page.getByText("Role Sync requires an enterprise license."),
  ).toBeVisible();
  await expect(page.getByText("Groups Attribute")).toHaveCount(0);
});
