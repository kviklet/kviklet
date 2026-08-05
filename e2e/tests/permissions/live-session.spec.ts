import { test, expect, Page } from "@playwright/test";
import {
  adminApi,
  apiLogin,
  createConnection,
  createRequest,
  createRestrictedUser,
  createRole,
  createUser,
  setUserRoles,
  approveRequest,
  uiLogin,
} from "./helpers";

// Findings T2-16 (Run Query), T2-17 (Execute and Download), T2-18 (Monaco
// editability) and T3-D (failed session load shows an access state) on the live
// session page /requests/<id>/session (LiveSessionWebsockets.tsx).
//
// TemporaryAccess execution is author-only AND needs execution_request:execute
// on the connection; the request must be approved by a different user holding
// execution_request:review (admin here).

const SLUG = `setd-${Date.now()}`;

async function loginAndOpenSession(
  page: Page,
  email: string,
  password: string,
  requestId: string,
) {
  await uiLogin(page, email, password);
  await page.waitForURL((u) => !u.pathname.includes("login"), {
    timeout: 15000,
  });
  await page.goto(`/requests/${requestId}/session`);
}

async function createApprovedTempAccessRequest(
  authorEmail: string,
  authorPassword: string,
  connectionId: string,
  title: string,
): Promise<string> {
  const authorCtx = await apiLogin(authorEmail, authorPassword);
  const requestId = await createRequest(authorCtx, connectionId, title, {
    type: "TemporaryAccess",
    statement: undefined,
    temporaryAccessDuration: 60,
  });
  const admin = await adminApi();
  await approveRequest(admin, requestId);
  return requestId;
}

test.describe("live session permission gating (T2-16/T2-17/T2-18/T3-D)", () => {
  test("author WITH execute: Run Query enabled, Monaco editable", async ({
    page,
  }) => {
    test.setTimeout(60000);
    const admin = await adminApi();
    const connectionId = `${SLUG}-conn-exec`;
    await createConnection(admin, connectionId, `${SLUG} exec connection`, {
      temporaryAccessEnabled: true,
    });
    const author = await createRestrictedUser(admin, `${SLUG}-author-exec`, [
      { action: "execution_request:edit", resource: "*" },
      { action: "execution_request:execute", resource: "*" },
    ]);
    const requestId = await createApprovedTempAccessRequest(
      author.email,
      author.password,
      connectionId,
      `${SLUG} temp access with execute`,
    );

    // Precondition: backend resolves execute on this request for the author.
    const authorCtx = await apiLogin(author.email, author.password);
    const reqJson = await (
      await authorCtx.get(`/api/execution-requests/${requestId}`)
    ).json();
    expect(reqJson.permissions).toContain("execution_request:execute");

    await loginAndOpenSession(page, author.email, author.password, requestId);

    // T2-16 positive: Run Query enabled, no permission tooltip.
    const runButton = page.getByTestId("run-query-button");
    await expect(runButton).toBeVisible({ timeout: 20000 });
    await expect(runButton).toBeEnabled();
    expect(await runButton.getAttribute("title")).toBeFalsy();

    // T2-18 positive: Monaco accepts typing. (Scope to [role="code"]: monaco's
    // rename widget also carries the .monaco-editor class.)
    const wrapper = page.getByTestId("monaco-editor-wrapper");
    const editor = wrapper.locator('.monaco-editor[role="code"]');
    await expect(editor).toBeVisible({ timeout: 20000 });
    await editor.click();
    await page.keyboard.type("SELECT 42;");
    await expect(editor.locator(".view-lines").first()).toContainText(
      "SELECT",
      { timeout: 10000 },
    );
  });

  test("author WITHOUT execute: Run Query disabled w/ tooltip, download disabled, Monaco read-only", async ({
    page,
  }) => {
    test.setTimeout(60000);
    const admin = await adminApi();
    const connectionId = `${SLUG}-conn-noexec`;
    await createConnection(admin, connectionId, `${SLUG} noexec connection`, {
      temporaryAccessEnabled: true,
    });
    // Default role (kept by createRestrictedUser) only grants *:get — no execute.
    const author = await createRestrictedUser(admin, `${SLUG}-author-noexec`, [
      { action: "execution_request:edit", resource: "*" },
    ]);
    const requestId = await createApprovedTempAccessRequest(
      author.email,
      author.password,
      connectionId,
      `${SLUG} temp access without execute`,
    );

    // Precondition: backend does NOT resolve execute for the author.
    const authorCtx = await apiLogin(author.email, author.password);
    const reqJson = await (
      await authorCtx.get(`/api/execution-requests/${requestId}`)
    ).json();
    expect(reqJson.permissions ?? []).not.toContain(
      "execution_request:execute",
    );

    await loginAndOpenSession(page, author.email, author.password, requestId);

    // T2-16 negative: Run Query disabled with a permission tooltip.
    const runButton = page.getByTestId("run-query-button");
    await expect(runButton).toBeVisible({ timeout: 20000 });
    await expect(runButton).toBeDisabled();
    await expect(runButton).toHaveAttribute("title", /permission/i);

    // T2-17: Execute and Download Results (relational Postgres connection so it
    // is rendered) disabled with a permission tooltip.
    const downloadButton = page.getByRole("button", {
      name: "Execute and Download Results",
    });
    await expect(downloadButton).toBeVisible();
    await expect(downloadButton).toBeDisabled();
    await expect(downloadButton).toHaveAttribute("title", /permission/i);

    // T2-18 negative: typing into Monaco does not change its content.
    const wrapper = page.getByTestId("monaco-editor-wrapper");
    const editor = wrapper.locator('.monaco-editor[role="code"]');
    await expect(editor).toBeVisible({ timeout: 20000 });
    // Behavioral read-only check: the same click+type mechanics demonstrably
    // insert text in the with-execute test above, so no content change here
    // means the editor rejected the input (monaco does not mirror readOnly to
    // a textarea attribute in this version).
    await editor.click();
    await page.keyboard.type("SHOULDNOTAPPEAR");
    await page.waitForTimeout(750);
    await expect(editor.locator(".view-lines").first()).not.toContainText(
      "SHOULDNOTAPPEAR",
    );
  });

  test("failed session load shows an access state, not infinite Loading", async ({
    page,
  }) => {
    test.setTimeout(60000);
    const admin = await adminApi();
    const connectionId = `${SLUG}-conn-hidden`;
    const otherConnectionId = `${SLUG}-conn-other`;
    await createConnection(admin, connectionId, `${SLUG} hidden connection`, {
      temporaryAccessEnabled: true,
    });
    await createConnection(admin, otherConnectionId, `${SLUG} other connection`);
    const requestId = await createRequest(
      admin,
      connectionId,
      `${SLUG} request on hidden connection`,
    );

    // Attempt to build a user with NO execution_request:get on the request's
    // connection: only a role scoped to a different connection. The backend
    // REFUSES to drop the mandatory default role (400 "Every User has to keep
    // the default role"), and the default role grants execution_request:get on
    // "*", so a genuine per-user 403 on the request GET is unreachable through
    // supported APIs. Document that constraint here, then exercise the 403 UI
    // path via a network-level 403 and the real-backend failure path via a
    // nonexistent request id.
    const email = `${SLUG}-noget@e2e.test`;
    const password = "e2e-password-1";
    const userId = await createUser(admin, email, password, `E2E ${SLUG} noget`);
    const roleId = await createRole(admin, `e2e-${SLUG}-noget`, [
      { action: "execution_request:get", resource: otherConnectionId },
    ]);
    let dropDefaultRoleError = "";
    try {
      await setUserRoles(admin, userId, [roleId]);
    } catch (e) {
      dropDefaultRoleError = e instanceof Error ? e.message : String(e);
    }
    expect(dropDefaultRoleError).toContain(
      "Every User has to keep the default role",
    );

    // Sanity: what the user actually holds (default role kept by the backend).
    const userCtx = await apiLogin(email, password);
    const status = await (await userCtx.get("/api/status")).json();
    expect(status.permissions).toContain("execution_request:get");
    expect(status.permissions).not.toContain("execution_request:execute");

    await uiLogin(page, email, password);
    await page.waitForURL((u) => !u.pathname.includes("login"), {
      timeout: 15000,
    });

    // (a) Real backend failure: a request id this user cannot load (does not
    // exist) must render the explicit access state, not "Loading..." forever.
    await page.goto(`/requests/${SLUG}-does-not-exist/session`);
    await expect(page.getByTestId("not-authorized")).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText("Loading...")).toHaveCount(0);

    // (b) 403-specific path: force the request GET to 403 at the network layer
    // (the shape the backend's ExceptionHandler produces) and confirm the real
    // UI renders the access state for it too.
    await page.route(`**/execution-requests/${requestId}`, (route) =>
      route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ message: "Access denied" }),
      }),
    );
    await page.goto(`/requests/${requestId}/session`);
    await expect(page.getByTestId("not-authorized")).toBeVisible({
      timeout: 20000,
    });
    await expect(page.getByText("Loading...")).toHaveCount(0);
  });
});
