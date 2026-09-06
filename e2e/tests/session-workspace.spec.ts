import { test, expect } from "@playwright/test";

// Each test owns its request; queries only select constants from the e2e database.
test.beforeEach(async ({ request, context }) => {
  const login = await request.post("/api/login", {
    headers: { "X-Kviklet-Request": "true" },
    data: { email: "admin@admin.com", password: "admin" },
  });
  expect(login.ok()).toBeTruthy();
  await context.addCookies((await request.storageState()).cookies);
});

for (const theme of ["light", "dark"] as const) {
  test(`session workspace preserves work across tabs (${theme})`, async ({
    page,
    request,
  }, testInfo) => {
    test.setTimeout(60_000);
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.setViewportSize({ width: 1512, height: 1000 });
    const connectionId = `workspace-${theme}-${Date.now()}`;
    const headers = { "X-Kviklet-Request": "true" };
    const connection = await request.post("/api/connections/", {
      headers,
      data: {
        connectionType: "DATASOURCE",
        id: connectionId,
        displayName: "Workspace test database",
        description: "Session workspace browser test",
        username: "postgres",
        password: "postgres",
        hostname: "postgres",
        port: 5432,
        databaseName: "postgres",
        type: "POSTGRESQL",
        reviewConfig: { numTotalRequired: 0 },
        temporaryAccessEnabled: true,
      },
    });
    expect(connection.ok()).toBeTruthy();
    const created = await request.post("/api/execution-requests/", {
      headers,
      data: {
        connectionType: "DATASOURCE",
        connectionId,
        title: "Investigate shipping delays",
        type: "TemporaryAccess",
        description:
          "Check the current shipping state and investigate delayed deliveries.",
        temporaryAccessDuration: 60,
      },
    });
    expect(created.ok()).toBeTruthy();
    const { id } = await created.json();
    const base = `/requests/${encodeURIComponent(id)}`;
    await page.addInitScript(
      (value) => localStorage.setItem("theme", value),
      theme,
    );
    await page.goto(base);
    await page.getByRole("link", { name: "Session", exact: true }).click();
    await expect(page.getByTestId("session-access-status")).toContainText(
      "Starts with your first query",
    );
    await expect(
      page.getByRole("region", { name: "Session workspace" }),
    ).toHaveAttribute("aria-busy", "false");
    await page.getByTestId("monaco-editor-wrapper").click();
    await page.keyboard.type("SELECT 42 AS answer;");
    const run = page.getByTestId("run-query-button").filter({ visible: true });
    await expect(run).toBeEnabled();
    await page.keyboard.press("Control+A");
    await expect(run).toHaveText("Run selection");
    await page.keyboard.press("Control+Enter");
    await expect(
      page.getByTestId("result-component").filter({ visible: true }),
    ).toContainText("42");
    await expect(page.getByTestId("session-access-status")).toContainText(
      "remaining",
    );
    await page.getByRole("link", { name: "Overview", exact: true }).click();
    await page.getByRole("link", { name: "Session", exact: true }).click();
    await expect(
      page.getByRole("textbox", { name: /Session query editor/ }),
    ).toHaveValue("SELECT 42 AS answer;");
    await expect(
      page.getByTestId("result-component").filter({ visible: true }),
    ).toContainText("42");
    const resize = page.getByRole("separator", { name: "Resize query editor" });
    await resize.focus();
    await page.keyboard.press("ArrowDown");
    await expect(resize).toHaveAttribute("aria-valuenow", "304");
    await expect(
      page.getByRole("button", { name: "Activity", exact: true }),
    ).toHaveAttribute("aria-expanded", "false");
    await page
      .getByRole("heading", { name: "Investigate shipping delays" })
      .click();
    await page.screenshot({
      path: testInfo.outputPath(`session-${theme}.png`),
      fullPage: true,
    });
    await page.getByRole("button", { name: "More execution options" }).click();
    const download = page.waitForEvent("download");
    await page.getByRole("menuitem", { name: /Run and download/ }).click();
    expect(await (await download).failure()).toBeNull();
    await page.getByRole("button", { name: "Activity", exact: true }).click();
    await expect(page.locator("#session-activity")).toContainText(
      "SELECT 42 AS answer;",
    );
    // Expiry is visible without requiring a navigation or another execution.
    await page.clock.install();
    await page.clock.fastForward(61 * 60_000);
    await expect(page.getByTestId("session-access-status")).toContainText(
      "Access expired",
    );
    await expect(run).toBeDisabled();
    await expect(
      page.getByRole("textbox", { name: /Session query editor/ }),
    ).toHaveValue("SELECT 42 AS answer;");
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(
      page.getByRole("link", { name: "Overview", exact: true }),
    ).toBeVisible();
    const workspace = await page
      .getByRole("region", { name: "Session workspace" })
      .boundingBox();
    expect(workspace!.x + workspace!.width).toBeLessThanOrEqual(390);
    await page.screenshot({
      path: testInfo.outputPath(`session-mobile-${theme}.png`),
      fullPage: true,
    });
    expect(errors).toEqual([]);
  });
}

test("pending sessions explain why execution is unavailable", async ({
  page,
  request,
}) => {
  const headers = { "X-Kviklet-Request": "true" };
  const connectionId = `workspace-pending-${Date.now()}`;
  const connection = await request.post("/api/connections/", {
    headers,
    data: {
      connectionType: "DATASOURCE",
      id: connectionId,
      displayName: "Approval required",
      username: "postgres",
      password: "postgres",
      hostname: "postgres",
      port: 5432,
      databaseName: "postgres",
      type: "POSTGRESQL",
      reviewConfig: { numTotalRequired: 1 },
      temporaryAccessEnabled: true,
    },
  });
  expect(connection.ok()).toBeTruthy();
  const created = await request.post("/api/execution-requests/", {
    headers,
    data: {
      connectionType: "DATASOURCE",
      connectionId,
      title: "Pending session",
      type: "TemporaryAccess",
      description: "Verify approval gating",
      temporaryAccessDuration: 60,
    },
  });
  expect(created.ok()).toBeTruthy();
  const { id } = await created.json();
  await page.goto(`/requests/${encodeURIComponent(id)}`);
  await page.getByRole("link", { name: "Session", exact: true }).click();
  await expect(page.getByTestId("session-access-status")).toContainText(
    "Awaiting approval",
  );
  await expect(page.getByTestId("read-only-banner")).toContainText("approved");
  await expect(page.getByTestId("run-query-button")).toBeDisabled();
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  await expect(page.getByText("Verify approval gating")).toBeVisible();
});
