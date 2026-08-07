import { test, expect } from "@playwright/test";
import { LoginPage, SettingsPage } from "../pages";

/**
 * Permission gating on the settings pages.
 *
 * All state is produced through the UI: an admin creates the roles and users
 * in beforeAll, and every test logs in as the user it wants to assert on.
 *
 * Users:
 * - viewer: holds only the always-assigned Default role (read-only access).
 * - roleViewer: Default + a custom role with the "View Roles" permission.
 */

const RUN = Date.now();
const PASSWORD = "e2e-permission-password";

const viewer = {
  name: `Perm Viewer ${RUN}`,
  email: `perm-viewer-${RUN}@example.com`,
};
const roleViewerRole = `Perm Role Viewer ${RUN}`;
const roleViewer = {
  name: `Perm Role Viewer User ${RUN}`,
  email: `perm-roleviewer-${RUN}@example.com`,
};

const ADMIN = { email: "admin@admin.com", password: "admin" };

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ browser }) => {
  test.setTimeout(120_000);
  const context = await browser.newContext();
  const page = await context.newPage();
  const loginPage = new LoginPage(page);
  const settingsPage = new SettingsPage(page);

  await page.goto("/");
  await loginPage.login(ADMIN.email, ADMIN.password);

  await settingsPage.addUserWithRoles(viewer.name, viewer.email, PASSWORD);

  await settingsPage.createRole({
    name: roleViewerRole,
    description: "Can view roles, nothing else beyond the default",
    roleRead: true,
  });
  await settingsPage.addUserWithRoles(
    roleViewer.name,
    roleViewer.email,
    PASSWORD,
    [roleViewerRole],
  );

  await context.close();
});

test("a user with only the default role lands on a restricted settings area", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  // Without configuration:get the settings landing redirects to the profile.
  await page.goto("/settings");
  await page.waitForURL("**/settings/profile");

  // Tabs covered by the default role are visible ...
  await expect(page.getByTestId("settings-profile")).toBeVisible();
  await expect(page.getByTestId("settings-users")).toBeVisible();
  await expect(page.getByTestId("settings-connections")).toBeVisible();
  // ... everything else is not.
  await expect(page.getByTestId("settings-general")).toHaveCount(0);
  await expect(page.getByTestId("settings-roles")).toHaveCount(0);
  await expect(page.getByTestId("settings-role-sync")).toHaveCount(0);
  await expect(page.getByTestId("settings-api-keys")).toHaveCount(0);

  // The profile page is read-only without user:edit.
  await expect(page.getByText(viewer.email)).toBeVisible();
  await expect(
    page.getByText("Profile editing is not enabled for your role."),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "Save" })).toHaveCount(0);
  await expect(page.locator('input[type="password"]')).toHaveCount(0);
});

test("a user without user management permissions sees a restricted users page", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/settings/users");
  await expect(page.getByTestId(`user-${ADMIN.email}`)).toBeVisible();

  // No user:create -> no Add User button.
  await expect(page.getByTestId("add-user-button")).toHaveCount(0);

  // No user:edit_roles -> read-only role names instead of the combobox.
  await expect(page.getByTestId("role-combobox-button")).toHaveCount(0);
  const readOnlyRoles = page
    .getByTestId(`user-${viewer.email}`)
    .locator('[title="You lack permission to change user roles."]');
  await expect(readOnlyRoles).toBeVisible();
  await expect(readOnlyRoles).toContainText("Default");
});

test("the roles page is guarded for a default-role user", async ({ page }) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/settings/roles");
  await expect(page.getByTestId("not-authorized")).toBeVisible();

  await page.goto("/settings/roles/new");
  await expect(page.getByTestId("not-authorized")).toBeVisible();
});

test("a default-role user can see the license page but cannot upload", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/settings/license");
  await expect(page.getByText("License Valid until")).toBeVisible();
  await expect(page.getByText("Click to upload")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Upload" })).toHaveCount(0);
});

test("a user with the view-roles permission gets read-only roles surfaces", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(roleViewer.email, PASSWORD);

  await page.goto("/settings/roles");
  await expect(page.getByTestId("roles-table")).toBeVisible();

  // No role:edit -> no create button and no delete icons.
  await expect(page.getByTestId("roles-table-create-button")).toHaveCount(0);
  await expect(
    page.locator('[data-testid^="roles-table-delete-"]'),
  ).toHaveCount(0);

  // The new-role route is guarded as well.
  await page.goto("/settings/roles/new");
  await expect(page.getByTestId("not-authorized")).toBeVisible();

  // Role details render read-only with an explanation.
  await page.goto("/settings/roles");
  await page
    .getByTestId("roles-table")
    .getByRole("row")
    .filter({ has: page.locator("td", { hasText: /^Default$/ }) })
    .click();
  await page.waitForURL(/\/settings\/roles\/[^/]+$/);
  await expect(page.getByTestId("read-only-badge")).toBeVisible();
  const nameInput = page.locator('input[name="name"]');
  await expect(nameInput).toBeVisible();
  await expect(nameInput).toBeDisabled();
  await expect(page.getByTestId("role-submit-button")).toHaveCount(0);
});

test("an admin sees the full settings area", async ({ page }) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(ADMIN.email, ADMIN.password);

  await page.goto("/settings");
  await expect(
    page.getByRole("heading", { name: "General Settings" }),
  ).toBeVisible();
  await expect(page.getByTestId("settings-general")).toBeVisible();
  await expect(page.getByTestId("settings-roles")).toBeVisible();
  await expect(page.getByTestId("settings-connections")).toBeVisible();
  await expect(page.getByTestId("settings-users")).toBeVisible();

  await page.goto("/settings/users");
  await expect(page.getByTestId("add-user-button")).toBeVisible();
  await expect(page.getByTestId("role-combobox-button").first()).toBeVisible();

  await page.goto("/settings/roles");
  await expect(page.getByTestId("roles-table-create-button")).toBeVisible();
  await expect(
    page.locator('[data-testid^="roles-table-delete-"]').first(),
  ).toBeVisible();

  // License upload is available with configuration:edit.
  await page.goto("/settings/license");
  await expect(page.getByText("Click to upload")).toBeVisible();
  await expect(page.getByRole("button", { name: "Upload" })).toBeVisible();
});
