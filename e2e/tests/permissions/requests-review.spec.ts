import { test, expect, Page, APIRequestContext } from "@playwright/test";
import {
  adminApi,
  apiLogin,
  approveRequest,
  createConnection,
  createRequest,
  createRestrictedUser,
  uiLogin,
} from "./helpers";

/**
 * Validator set C: requests list / review page / audit log permission UI.
 * Findings: T1-01, T1-03, T1-23, T1-24, T1-25, T3-B, T3-C, T2-09..T2-15.
 *
 * Note on the zero-grant user: the backend refuses to remove the default role from
 * a user ("Every User has to keep the default role"), so the only way a user can
 * hold no execution_request:get is for an admin to edit the default role's policies
 * (which the API allows — isDefault is derived from a fixed role id, not a flag).
 * The zero-grant test below strips execution_request:get from the default role,
 * asserts everything in one window, and restores the original policies in finally.
 */

const HEADERS = { "X-Kviklet-Request": "e2e" };
const EXECUTE_TOOLTIP = "You lack permission to execute on this connection";

const suffix = Date.now();

interface Creds {
  email: string;
  password: string;
  userId: string;
}

let zero: Creds; // default role only; used while default role is stripped of execution_request:get
let viewer: Creds; // default role only (get on everything, nothing else)
let reviewer: Creds; // default + execution_request:review/*
let executor: Creds; // default + execution_request:execute/*
let authorNoExec: Creds; // default + execution_request:edit/*
let authorExec: Creds; // default + execution_request:edit/* + execute/*

let reqSingle: string; // approved SingleExecution on datasource conn, author = authorExec
let reqTempNoExec: string; // approved TemporaryAccess, author = authorNoExec
let reqTempExec: string; // approved TemporaryAccess, author = authorExec
let reqK8s: string; // approved SingleExecution on kubernetes conn, author = authorExec

async function createK8sConnection(
  api: APIRequestContext,
  id: string,
): Promise<string> {
  const res = await api.post(`/api/connections/`, {
    headers: HEADERS,
    data: {
      connectionType: "KUBERNETES",
      id,
      displayName: `SetC K8s ${id}`,
      description: "e2e permission test k8s connection",
      reviewConfig: { numTotalRequired: 1 },
    },
  });
  if (!res.ok()) {
    throw new Error(
      `createK8sConnection failed: ${res.status()} ${await res.text()}`,
    );
  }
  return (await res.json()).id as string;
}

async function createK8sRequest(
  api: APIRequestContext,
  connectionId: string,
  title: string,
): Promise<string> {
  const res = await api.post(`/api/execution-requests/`, {
    headers: HEADERS,
    data: {
      connectionType: "KUBERNETES",
      connectionId,
      title,
      type: "SingleExecution",
      description: "e2e permission test k8s request",
      namespace: "default",
      podName: "test-pod",
      containerName: null,
      command: "echo hello",
    },
  });
  if (!res.ok()) {
    throw new Error(
      `createK8sRequest failed: ${res.status()} ${await res.text()}`,
    );
  }
  return (await res.json()).id as string;
}

interface RolePolicy {
  action: string;
  resource: string;
}

async function getDefaultRole(
  api: APIRequestContext,
): Promise<{ id: string; policies: RolePolicy[] }> {
  const res = await api.get(`/api/roles/`);
  const roles = (await res.json()).roles as {
    id: string;
    isDefault: boolean;
    policies: RolePolicy[];
  }[];
  const def = roles.find((r) => r.isDefault);
  if (!def) throw new Error("default role not found");
  return { id: def.id, policies: def.policies };
}

async function setRolePolicies(
  api: APIRequestContext,
  roleId: string,
  policies: RolePolicy[],
): Promise<void> {
  const res = await api.patch(`/api/roles/${roleId}`, {
    headers: HEADERS,
    data: {
      id: roleId,
      name: null,
      description: null,
      policies: policies.map((p) => ({
        id: null,
        action: p.action,
        resource: p.resource,
      })),
    },
  });
  if (!res.ok()) {
    throw new Error(
      `setRolePolicies failed: ${res.status()} ${await res.text()}`,
    );
  }
}

/** Fresh browser login (clears any previous user's cookies first). */
async function login(page: Page, user: Creds) {
  await page.context().clearCookies();
  await uiLogin(page, user.email, user.password);
  await page.waitForURL((url) => !url.pathname.includes("login"));
}

function reviewUrl(requestId: string): string {
  return `/requests/${encodeURIComponent(requestId.trim())}`;
}

test.beforeAll(async () => {
  test.setTimeout(120_000);
  const api = await adminApi();

  zero = await createRestrictedUser(api, `setC-zero-${suffix}`, []);
  viewer = await createRestrictedUser(api, `setC-viewer-${suffix}`, []);
  reviewer = await createRestrictedUser(api, `setC-reviewer-${suffix}`, [
    { action: "execution_request:review", resource: "*" },
  ]);
  executor = await createRestrictedUser(api, `setC-executor-${suffix}`, [
    { action: "execution_request:execute", resource: "*" },
  ]);
  authorNoExec = await createRestrictedUser(
    api,
    `setC-author-noexec-${suffix}`,
    [{ action: "execution_request:edit", resource: "*" }],
  );
  authorExec = await createRestrictedUser(api, `setC-author-exec-${suffix}`, [
    { action: "execution_request:edit", resource: "*" },
    { action: "execution_request:execute", resource: "*" },
  ]);

  const connMain = await createConnection(
    api,
    `setc-main-${suffix}`,
    `SetC Main ${suffix}`,
    { dryRunEnabled: true, dryRunRequiresApproval: false },
  );
  const connK8s = await createK8sConnection(api, `setc-k8s-${suffix}`);

  const authorExecApi = await apiLogin(authorExec.email, authorExec.password);
  const authorNoExecApi = await apiLogin(
    authorNoExec.email,
    authorNoExec.password,
  );

  reqSingle = await createRequest(authorExecApi, connMain, "SetC single exec");
  reqTempNoExec = await createRequest(
    authorNoExecApi,
    connMain,
    "SetC temp noexec",
    { type: "TemporaryAccess", statement: null, temporaryAccessDuration: 60 },
  );
  reqTempExec = await createRequest(authorExecApi, connMain, "SetC temp exec", {
    type: "TemporaryAccess",
    statement: null,
    temporaryAccessDuration: 60,
  });
  reqK8s = await createK8sRequest(authorExecApi, connK8s, "SetC k8s exec");

  // Admin (not the author) approves everything.
  for (const id of [reqSingle, reqTempNoExec, reqTempExec, reqK8s]) {
    await approveRequest(api, id);
  }
});

// ---------------------------------------------------------------------------
// Zero-grant scenarios: T1-01, T1-03, T1-23, T3-B, T3-C + zero half of T1-25.
// All in one test to keep the default-role mutation window as short as possible.
// ---------------------------------------------------------------------------
test("T1-01/T1-03/T1-23/T3-B/T3-C: user with no execution_request:get anywhere", async ({
  page,
}) => {
  test.setTimeout(90_000);
  const api = await adminApi();
  const defaultRole = await getDefaultRole(api);
  const strippedPolicies = defaultRole.policies.filter(
    (p) => p.action !== "execution_request:get",
  );
  expect(strippedPolicies.length).toBeLessThan(defaultRole.policies.length);
  await setRolePolicies(api, defaultRole.id, strippedPolicies);
  try {
    // Guard: the zero-grant user really holds no execution_request:get now.
    const zeroApi = await apiLogin(zero.email, zero.password);
    const zeroStatus = await (await zeroApi.get("/api/status")).json();
    expect(zeroStatus.permissions).not.toContain("execution_request:get");

    await login(page, zero);

    // T1-03: landing on "/" routes to profile settings, no 403 toast, no backstop.
    await page.goto("/");
    await page.waitForURL("**/settings/profile");
    await expect(page.getByTestId("settings-dropdown")).toBeVisible();
    await expect(page.getByText("Failed to fetch requests")).toHaveCount(0);
    await expect(page.getByTestId("not-authorized")).toHaveCount(0);

    // T1-01: Requests nav entry hidden.
    await expect(page.getByTestId("requests-link")).toHaveCount(0);

    // T1-23: /requests renders the NotAuthorized fallback, not a toast + empty list.
    await page.goto("/requests");
    await expect(page.getByTestId("not-authorized")).toBeVisible();
    await expect(page.getByText("Failed to fetch requests")).toHaveCount(0);

    // T3-B: audit log surfaces an explicit failure state, not a silently empty list.
    // T1-25 (zero half): the Export button is not rendered at all.
    await page.goto("/auditlog");
    await expect(page.getByTestId("not-authorized")).toBeVisible();
    await expect(page.getByRole("button", { name: "Export" })).toHaveCount(0);

    // T3-C: review page shows NotAuthorized instead of a blank div.
    await page.goto(reviewUrl(reqSingle));
    await expect(page.getByTestId("not-authorized")).toBeVisible();
  } finally {
    await setRolePolicies(api, defaultRole.id, defaultRole.policies);
  }
});

// ---------------------------------------------------------------------------
// T1-01 (positive half): default-role user still sees the Requests nav link
// ---------------------------------------------------------------------------
test("T1-01: Requests nav link visible for a default-role user", async ({
  page,
}) => {
  test.setTimeout(60_000);
  await login(page, viewer);
  await expect(page.getByTestId("requests-link")).toBeVisible();
});

// ---------------------------------------------------------------------------
// T1-24: review pills gated on object-scoped execution_request:review
// ---------------------------------------------------------------------------
test("T1-24: review pills hidden without review permission, shown with it, own-request gating kept", async ({
  page,
}) => {
  test.setTimeout(60_000);

  // Default-role user (no review permission): comment only.
  await login(page, viewer);
  await page.goto(reviewUrl(reqSingle));
  await page.getByTestId("expand-comment-box").click();
  await expect(page.getByTestId("review-type-Comment")).toBeVisible();
  await expect(page.getByTestId("review-type-Approve")).toHaveCount(0);
  await expect(page.getByTestId("review-type-Request Changes")).toHaveCount(0);
  await expect(page.getByTestId("review-type-Reject")).toHaveCount(0);

  // Reviewer (execution_request:review/*): all three pills present.
  await login(page, reviewer);
  await page.goto(reviewUrl(reqSingle));
  await page.getByTestId("expand-comment-box").click();
  await expect(page.getByTestId("review-type-Approve")).toBeVisible();
  await expect(page.getByTestId("review-type-Request Changes")).toBeVisible();
  await expect(page.getByTestId("review-type-Reject")).toBeVisible();

  // Author on their own request: no Approve (self-review gating kept), Close offered.
  await login(page, authorExec);
  await page.goto(reviewUrl(reqSingle));
  await page.getByTestId("expand-comment-box").click();
  await expect(page.getByTestId("review-type-Close")).toBeVisible();
  await expect(page.getByTestId("review-type-Approve")).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// T1-25 (positive half): Export renders only as the license lock for a user
// with execution_request:get (no license on this stack)
// ---------------------------------------------------------------------------
test("T1-25: Export button license-locked (disabled) for user with execution_request:get", async ({
  page,
}) => {
  test.setTimeout(60_000);
  await login(page, viewer);
  await page.goto("/auditlog");
  const exportButton = page.getByRole("button", { name: "Export" });
  await expect(exportButton).toBeVisible();
  await expect(exportButton).toBeDisabled();
});

// ---------------------------------------------------------------------------
// T2-09: Run Query primary button gated on object-scoped execute
// ---------------------------------------------------------------------------
test("T2-09: Run Query disabled with permission tooltip without execute, enabled with it", async ({
  page,
}) => {
  test.setTimeout(60_000);

  // Reviewer (no execute) on an approved request they did not author.
  await login(page, reviewer);
  await page.goto(reviewUrl(reqSingle));
  const runButton = page.getByTestId("run-query-button");
  await expect(runButton).toBeVisible();
  await expect(runButton).toBeDisabled();
  await expect(runButton).toHaveAttribute("title", EXECUTE_TOOLTIP);

  // Executor (execution_request:execute/*) sees it enabled.
  await login(page, executor);
  await page.goto(reviewUrl(reqSingle));
  const runButtonExec = page.getByTestId("run-query-button");
  await expect(runButtonExec).toBeVisible();
  await expect(runButtonExec).toBeEnabled();
});

// ---------------------------------------------------------------------------
// T2-10 + T2-11: Execute-and-Download and Dry Run menu items gated on execute
// ---------------------------------------------------------------------------
test("T2-10/T2-11: Download and Dry Run menu items disabled with tooltip without execute, enabled with it", async ({
  page,
}) => {
  test.setTimeout(60_000);

  await login(page, reviewer);
  await page.goto(reviewUrl(reqSingle));
  await expect(page.getByTestId("run-query-button")).toBeVisible();
  await page.getByRole("button", { name: "Open options" }).click();

  const download = page.getByRole("menuitem", {
    name: "Execute and Download Results",
  });
  await expect(download).toBeVisible();
  await expect(download).toHaveClass(/cursor-not-allowed/);
  await expect(download).toHaveAttribute("title", EXECUTE_TOOLTIP);

  const dryRun = page.getByRole("menuitem", { name: "Dry Run" });
  await expect(dryRun).toBeVisible();
  await expect(dryRun).toHaveClass(/cursor-not-allowed/);
  await expect(dryRun).toHaveAttribute("title", EXECUTE_TOOLTIP);

  // Executor: both items enabled.
  await login(page, executor);
  await page.goto(reviewUrl(reqSingle));
  await expect(page.getByTestId("run-query-button")).toBeVisible();
  await page.getByRole("button", { name: "Open options" }).click();
  await expect(
    page.getByRole("menuitem", { name: "Execute and Download Results" }),
  ).not.toHaveClass(/cursor-not-allowed/);
  await expect(page.getByRole("menuitem", { name: "Dry Run" })).not.toHaveClass(
    /cursor-not-allowed/,
  );
});

// ---------------------------------------------------------------------------
// T2-12: Start Proxy gated on author + approval + execute
// ---------------------------------------------------------------------------
test("T2-12: Start Proxy disabled with permission tooltip for author without execute, enabled with execute, author-only kept", async ({
  page,
}) => {
  test.setTimeout(60_000);

  // Author WITHOUT execute on their approved TemporaryAccess request.
  await login(page, authorNoExec);
  await page.goto(reviewUrl(reqTempNoExec));
  await expect(page.getByTestId("run-query-button")).toBeVisible();
  await page.getByRole("button", { name: "Open options" }).click();
  const proxyNoExec = page.getByRole("menuitem", { name: "Start Proxy" });
  await expect(proxyNoExec).toBeVisible();
  await expect(proxyNoExec).toHaveClass(/cursor-not-allowed/);
  await expect(proxyNoExec).toHaveAttribute("title", EXECUTE_TOOLTIP);

  // Author WITH execute: enabled.
  await login(page, authorExec);
  await page.goto(reviewUrl(reqTempExec));
  await expect(page.getByTestId("run-query-button")).toBeVisible();
  await page.getByRole("button", { name: "Open options" }).click();
  const proxyExec = page.getByRole("menuitem", { name: "Start Proxy" });
  await expect(proxyExec).toBeVisible();
  await expect(proxyExec).not.toHaveClass(/cursor-not-allowed/);

  // Non-author with execute: existing author-only gating kept.
  await login(page, executor);
  await page.goto(reviewUrl(reqTempExec));
  await expect(page.getByTestId("run-query-button")).toBeVisible();
  await page.getByRole("button", { name: "Open options" }).click();
  const proxyNonAuthor = page.getByRole("menuitem", { name: "Start Proxy" });
  await expect(proxyNonAuthor).toHaveClass(/cursor-not-allowed/);
  await expect(proxyNonAuthor).toHaveAttribute(
    "title",
    "Proxy access is granted only to the requester",
  );
});

// ---------------------------------------------------------------------------
// T2-13: click-to-edit SQL statement is author-only
// ---------------------------------------------------------------------------
test("T2-13: statement is read-only with explanation for non-authors, editable for the author", async ({
  page,
}) => {
  test.setTimeout(60_000);

  // Non-author: tooltip explains, clicking must not open edit mode.
  await login(page, reviewer);
  await page.goto(reviewUrl(reqSingle));
  const readOnlyBox = page.locator(
    'div[title="Only the requester can edit the statement"]',
  );
  await expect(readOnlyBox).toBeVisible();
  await readOnlyBox.click();
  await expect(page.locator("textarea#statement")).toHaveCount(0);

  // Author: clicking the statement opens the editor.
  await login(page, authorExec);
  await page.goto(reviewUrl(reqSingle));
  const editableBox = page
    .locator("div.cursor-pointer")
    .filter({ hasText: "SELECT" });
  await expect(editableBox).toBeVisible();
  await editableBox.click();
  await expect(page.locator("textarea#statement")).toBeVisible();
  await page.getByRole("button", { name: "Cancel" }).click();
  await expect(page.locator("textarea#statement")).toHaveCount(0);
});

// ---------------------------------------------------------------------------
// T2-14: Kubernetes Run Command gated on execute
// ---------------------------------------------------------------------------
test("T2-14: k8s Run Command disabled with permission tooltip without execute, enabled with it", async ({
  page,
}) => {
  test.setTimeout(60_000);

  await login(page, reviewer);
  await page.goto(reviewUrl(reqK8s));
  const runCommand = page.getByRole("button", { name: "Run Command" });
  await expect(runCommand).toBeVisible();
  await expect(runCommand).toBeDisabled();
  await expect(runCommand).toHaveAttribute("title", EXECUTE_TOOLTIP);

  await login(page, executor);
  await page.goto(reviewUrl(reqK8s));
  const runCommandExec = page.getByRole("button", { name: "Run Command" });
  await expect(runCommandExec).toBeVisible();
  await expect(runCommandExec).toBeEnabled();
});

// ---------------------------------------------------------------------------
// T2-15: Kubernetes Edit Command is author-only (disabled with tooltip)
// ---------------------------------------------------------------------------
test("T2-15: k8s Edit Command disabled with tooltip for non-authors, works for the author", async ({
  page,
}) => {
  test.setTimeout(60_000);

  await login(page, reviewer);
  await page.goto(reviewUrl(reqK8s));
  const editCommand = page.getByRole("button", { name: "Edit Command" });
  await expect(editCommand).toBeVisible();
  await expect(editCommand).toBeDisabled();
  await expect(editCommand).toHaveAttribute(
    "title",
    "Only the requester can edit the command",
  );

  await login(page, authorExec);
  await page.goto(reviewUrl(reqK8s));
  const editCommandAuthor = page.getByRole("button", { name: "Edit Command" });
  await expect(editCommandAuthor).toBeVisible();
  await expect(editCommandAuthor).toBeEnabled();
  await editCommandAuthor.click();
  await expect(page.locator("textarea")).toBeVisible();
  await page.getByRole("button", { name: "Cancel" }).click();
});
