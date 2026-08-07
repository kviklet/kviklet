import { test, expect } from "@playwright/test";
import {
  LoginPage,
  RequestsPage,
  RequestsReviewPage,
  SettingsPage,
} from "../pages";

/**
 * Permission gating around connections and execution requests.
 *
 * All state is produced through the UI: an admin creates connections, roles
 * and users in beforeAll, a writer user submits the requests, and an admin
 * approves one of them. Every test logs in as the user it wants to assert on.
 *
 * Requests:
 * - pendingQuery: submitted but not approved (drives the review-action tests).
 * - execQuery: approved by an admin (drives the execution tests).
 *
 * Users:
 * - viewer: holds only the always-assigned Default role (read-only access).
 * - writer: Default + write permission on connection A only.
 * - reviewer: Default + review permission on connection A.
 */

const RUN = Date.now();
const PASSWORD = "e2e-permission-password";

const connA = `Perm Conn A ${RUN}`;
const connB = `Perm Conn B ${RUN}`;
const pendingQuery = `Perm Pending Query ${RUN}`;
const execQuery = `Perm Exec Query ${RUN}`;

const viewer = { email: `perm-req-viewer-${RUN}@example.com` };
const writerRole = `Perm Writer ${RUN}`;
const writer = { email: `perm-writer-${RUN}@example.com` };
const reviewerRole = `Perm Reviewer ${RUN}`;
const reviewer = { email: `perm-reviewer-${RUN}@example.com` };

const ADMIN = { email: "admin@admin.com", password: "admin" };

const NO_REQUEST_TOOLTIP =
  "You lack permission to create requests on this connection";
const EXECUTE_TOOLTIP = "You lack permission to execute on this connection";

let connAId: string;
let connBId: string;

test.describe.configure({ mode: "serial" });

test.beforeAll(async ({ browser }) => {
  test.setTimeout(240_000);
  const context = await browser.newContext();
  const page = await context.newPage();
  const loginPage = new LoginPage(page);
  const settingsPage = new SettingsPage(page);
  const requestsPage = new RequestsPage(page);

  await page.goto("/");
  await loginPage.login(ADMIN.email, ADMIN.password);

  // One required review so requests actually go through the review flow.
  await settingsPage.navigateToConnections();
  await settingsPage.createConnection(
    connA,
    "Postgres",
    "postgres",
    "postgres",
    "postgres",
    "5432",
    "postgres",
    undefined,
    1,
  );
  await settingsPage.createConnection(
    connB,
    "Postgres",
    "postgres",
    "postgres",
    "postgres",
    "5432",
    "postgres",
    undefined,
    1,
  );
  connAId = await settingsPage.getConnectionId(connA);
  connBId = await settingsPage.getConnectionId(connB);

  await settingsPage.createRole({
    name: writerRole,
    description: "Can create and execute requests on one connection",
    connectionPolicies: [{ connectionId: connAId, write: true }],
  });
  await settingsPage.createRole({
    name: reviewerRole,
    description: "Can review requests on one connection",
    connectionPolicies: [{ connectionId: connAId, review: true }],
  });

  await settingsPage.addUserWithRoles(
    `Perm Viewer ${RUN}`,
    viewer.email,
    PASSWORD,
  );
  await settingsPage.addUserWithRoles(
    `Perm Writer ${RUN}`,
    writer.email,
    PASSWORD,
    [writerRole],
  );
  await settingsPage.addUserWithRoles(
    `Perm Reviewer ${RUN}`,
    reviewer.email,
    PASSWORD,
    [reviewerRole],
  );

  // The writer submits both requests through the same UI flow every user goes
  // through.
  await loginPage.logout();
  await loginPage.login(writer.email, PASSWORD);
  await requestsPage.createRequest(
    connA,
    pendingQuery,
    "Left pending for the review tests",
    "SELECT 1;",
  );
  await requestsPage.createRequest(
    connA,
    execQuery,
    "Approved for the execution tests",
    "SELECT 1;",
  );

  // An admin (not the author) approves only the execution request.
  await loginPage.logout();
  await loginPage.login(ADMIN.email, ADMIN.password);
  await new RequestsReviewPage(page, execQuery).approveRequest();

  await context.close();
});

test("a user without write permission sees only view-only connection cards", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/new");

  for (const connection of [connA, connB]) {
    const badge = page.getByTestId(`view-only-${connection}`);
    await expect(badge).toBeVisible();
    await expect(badge).toHaveAttribute("title", NO_REQUEST_TOOLTIP);
    await expect(page.getByTestId(`query-button-${connection}`)).toHaveCount(0);
    await expect(page.getByTestId(`access-button-${connection}`)).toHaveCount(
      0,
    );
  }
});

test("a writer sees action buttons only on the connection they can write to", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(writer.email, PASSWORD);

  await page.goto("/new");

  const queryButton = page.getByTestId(`query-button-${connA}`);
  await expect(queryButton).toBeEnabled();
  await expect(page.getByTestId(`access-button-${connA}`)).toBeEnabled();

  const badge = page.getByTestId(`view-only-${connB}`);
  await expect(badge).toBeVisible();
  await expect(badge).toHaveAttribute("title", NO_REQUEST_TOOLTIP);

  // The positive path really works: clicking opens the request form.
  await queryButton.click();
  await expect(page.getByTestId("request-title")).toBeVisible();
});

test("the connection list hides create entry points without create permission", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/settings/connections");
  await expect(page.getByTestId("connections-table")).toBeVisible();
  // The connections themselves are visible ...
  await expect(
    page.getByTestId(`connections-table-row-${connAId}`),
  ).toBeVisible();
  // ... but there is no way to reach the create modal.
  await expect(page.getByTestId("connections-table-create-button")).toHaveCount(
    0,
  );
  await expect(page.getByTestId("add-database-connection-button")).toHaveCount(
    0,
  );

  await loginPage.logout();
  await loginPage.login(ADMIN.email, ADMIN.password);
  await page.goto("/settings/connections");
  await expect(page.getByTestId("connections-table")).toBeVisible();
  await expect(
    page.getByTestId("connections-table-create-button").first(),
  ).toBeVisible();
});

test("connection details are read-only without edit permission", async ({
  page,
}) => {
  test.setTimeout(60_000);
  const loginPage = new LoginPage(page);
  const readOnlyBadge = page.getByTestId("read-only-badge");

  // A plain viewer cannot edit.
  await loginPage.loginFresh(viewer.email, PASSWORD);
  await page.goto(`/settings/connections/${connAId}`);
  await expect(readOnlyBadge).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Save", exact: true }),
  ).toHaveCount(0);
  await expect(
    page.getByRole("button", { name: "Delete", exact: true }),
  ).toHaveCount(0);
  await expect(page.locator("#displayName")).toBeDisabled();

  // A request-writer on the same connection cannot edit it either; request
  // write permission does not include connection edit permission.
  await loginPage.logout();
  await loginPage.login(writer.email, PASSWORD);
  await page.goto(`/settings/connections/${connAId}`);
  await expect(readOnlyBadge).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Save", exact: true }),
  ).toHaveCount(0);

  // An admin can edit: inputs work, Save enables once the form is dirty and
  // Delete is available.
  await loginPage.logout();
  await loginPage.login(ADMIN.email, ADMIN.password);
  await page.goto(`/settings/connections/${connAId}`);
  const save = page.getByRole("button", { name: "Save", exact: true });
  await expect(save).toBeVisible();
  await page.locator("#displayName").fill(`${connA} edited`);
  await expect(save).toBeEnabled();
  await expect(
    page.getByRole("button", { name: "Delete", exact: true }),
  ).toBeVisible();
  // Undo the rename so other tests keep finding the connection by name.
  await page.locator("#displayName").fill(connA);
});

test("a user without review permission can only comment", async ({ page }) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/");
  await new RequestsReviewPage(page, pendingQuery).navigate();
  await page.getByTestId("expand-comment-box").click();
  await expect(page.getByTestId("review-type-Comment")).toBeVisible();
  await expect(page.getByTestId("review-type-Approve")).toHaveCount(0);
  await expect(page.getByTestId("review-type-Request Changes")).toHaveCount(0);
  await expect(page.getByTestId("review-type-Reject")).toHaveCount(0);
});

test("a reviewer sees all review actions", async ({ page }) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(reviewer.email, PASSWORD);

  await page.goto("/");
  await new RequestsReviewPage(page, pendingQuery).navigate();
  await page.getByTestId("expand-comment-box").click();
  await expect(page.getByTestId("review-type-Approve")).toBeVisible();
  await expect(page.getByTestId("review-type-Request Changes")).toBeVisible();
  await expect(page.getByTestId("review-type-Reject")).toBeVisible();
});

test("authors cannot approve their own request but can close it", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(writer.email, PASSWORD);

  await page.goto("/");
  await new RequestsReviewPage(page, pendingQuery).navigate();
  await page.getByTestId("expand-comment-box").click();
  await expect(page.getByTestId("review-type-Close")).toBeVisible();
  await expect(page.getByTestId("review-type-Approve")).toHaveCount(0);
});

test("execution actions are disabled without execute permission", async ({
  page,
}) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(viewer.email, PASSWORD);

  await page.goto("/");
  await new RequestsReviewPage(page, execQuery).navigate();

  const runButton = page.getByTestId("run-query-button");
  await expect(runButton).toBeVisible();
  await expect(runButton).toBeDisabled();
  await expect(runButton).toHaveAttribute("title", EXECUTE_TOOLTIP);

  // Dry Run is a per-connection feature toggle (off by default), so only the
  // always-present download item is asserted here.
  await page.getByRole("button", { name: "Open options" }).click();
  const menuItem = page.getByRole("menuitem", {
    name: "Execute and Download Results",
  });
  await expect(menuItem).toBeVisible();
  await expect(menuItem).toHaveClass(/cursor-not-allowed/);
  await expect(menuItem).toHaveAttribute("title", EXECUTE_TOOLTIP);
});

test("execution actions are enabled for the writer", async ({ page }) => {
  test.setTimeout(30_000);
  const loginPage = new LoginPage(page);
  await loginPage.loginFresh(writer.email, PASSWORD);

  await page.goto("/");
  await new RequestsReviewPage(page, execQuery).navigate();

  const runButton = page.getByTestId("run-query-button");
  await expect(runButton).toBeVisible();
  await expect(runButton).toBeEnabled();

  await page.getByRole("button", { name: "Open options" }).click();
  await expect(
    page.getByRole("menuitem", { name: "Execute and Download Results" }),
  ).not.toHaveClass(/cursor-not-allowed/);
});

test("the statement is only editable by the requester", async ({ page }) => {
  test.setTimeout(60_000);
  const loginPage = new LoginPage(page);

  // A non-author sees a read-only statement; clicking does not open the editor.
  await loginPage.loginFresh(reviewer.email, PASSWORD);
  await page.goto("/");
  await new RequestsReviewPage(page, pendingQuery).navigate();
  const readOnlyBox = page.locator(
    'div[title="Only the requester can edit the statement"]',
  );
  await expect(readOnlyBox).toBeVisible();
  await readOnlyBox.click();
  await expect(page.locator("textarea#statement")).toHaveCount(0);

  // The author can open the editor.
  await loginPage.logout();
  await loginPage.login(writer.email, PASSWORD);
  await page.goto("/");
  await new RequestsReviewPage(page, pendingQuery).navigate();
  const editableBox = page
    .locator("div.cursor-pointer")
    .filter({ hasText: "SELECT" });
  await expect(editableBox).toBeVisible();
  await editableBox.click();
  await expect(page.locator("textarea#statement")).toBeVisible();
  await page.getByRole("button", { name: "Cancel" }).click();
});

// Keep this test last: it takes the writer's role away, and the earlier tests
// rely on the writer still holding it.
test("a session turns read-only when the author's write role is revoked", async ({
  page,
}) => {
  test.setTimeout(120_000);
  const loginPage = new LoginPage(page);
  const settingsPage = new SettingsPage(page);
  const sessionTitle = `Perm Revoked Session ${RUN}`;

  // The writer requests temporary access on their connection.
  await loginPage.loginFresh(writer.email, PASSWORD);
  await new RequestsPage(page).createSession(
    connA,
    sessionTitle,
    "Session for the revocation test",
  );
  await page.waitForURL("**/requests/*");
  const sessionUrl = `${new URL(page.url()).pathname}/session`;

  // An admin approves it, then removes the writer's role.
  await loginPage.logout();
  await loginPage.login(ADMIN.email, ADMIN.password);
  await new RequestsReviewPage(page, sessionTitle).approveRequest();
  await settingsPage.toggleUserRole(writer.email, writerRole, false);

  // Opening the (previously granted) session now renders it read-only.
  await loginPage.logout();
  await loginPage.login(writer.email, PASSWORD);
  await page.goto(sessionUrl);
  await expect(page.getByTestId("read-only-banner")).toBeVisible({
    timeout: 20_000,
  });
  const runButton = page.getByTestId("run-query-button");
  await expect(runButton).toBeDisabled();
  await expect(runButton).toHaveAttribute("title", /permission/i);
});
