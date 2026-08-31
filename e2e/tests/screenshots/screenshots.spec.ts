import { test, expect, Page, Locator } from "@playwright/test";
import * as path from "path";
import {
  seed,
  SeededData,
  HERO_REQUEST_TITLE,
  PROXY_REQUEST_TITLE,
  PENDING_REQUEST_TITLE,
  LIVE_SESSION_QUERY,
} from "./seed";

/**
 * Generates the README screenshots (images/*.png) from seeded demo data.
 *
 * Run with: npm run screenshots (requires the e2e stack, see e2e/Readme.md).
 * Compare against the committed images with: npm run screenshots:diff
 * Adopt the new screenshots into images/ with: npm run screenshots:update
 */

const OUTPUT_DIR = path.resolve(__dirname, "../../screenshots-output");
const ADMIN_STATE_PATH = path.resolve(__dirname, "../../.auth/admin.json");

const THEMES = ["light", "dark"] as const;
type Theme = (typeof THEMES)[number];

let seeded: SeededData;

test.beforeAll(async ({ baseURL }) => {
  if (!baseURL) {
    throw new Error("baseURL is not configured");
  }
  seeded = await seed(baseURL, ADMIN_STATE_PATH);
});

test.use({ storageState: ADMIN_STATE_PATH });

/** Applies the theme before any app code runs (the app reads localStorage). */
async function useTheme(page: Page, theme: Theme) {
  await page.addInitScript((t) => {
    window.localStorage.setItem("theme", t);
  }, theme);
}

async function shoot(
  page: Page,
  name: string,
  theme: Theme,
  target?: Locator,
) {
  // Web fonts still loading would shift text between runs.
  await page.evaluate(() => document.fonts.ready);
  const file = path.join(OUTPUT_DIR, `${name}_${theme}.png`);
  const options = {
    path: file,
    animations: "disabled",
    caret: "hide",
  } as const;
  if (target) {
    await target.screenshot(options);
  } else {
    await page.screenshot(options);
  }
}

/** Opens the "Add a new connection" modal on the connections settings page. */
async function openConnectionModal(page: Page) {
  await page.goto("/settings/connections");
  await page.getByTestId("connections-table-create-button").click();
  await page.getByTestId("add-database-connection-button").click();
  await expect(page.getByText("Add a new connection")).toBeVisible();
}

for (const theme of THEMES) {
  test.describe(`${theme} mode`, () => {
    test.beforeEach(async ({ page }) => {
      await useTheme(page, theme);
      // Pin the clock relative to the seeded data so relative timestamps
      // ("5 minutes ago") are identical on every run against the same stack.
      await page.clock.setFixedTime(seeded.clockAnchor);
    });

    test(`ExecutedRequest ${theme}`, async ({ page }) => {
      await page.goto(`/requests/${seeded.heroRequestId}`);
      await expect(
        page.getByRole("heading", { name: HERO_REQUEST_TITLE }),
      ).toBeVisible();
      // The activity feed with the approval comment and the executed statement
      // is the point of this screenshot; wait for all of it.
      await expect(page.getByText("Sure, looks good to me!")).toBeVisible();
      await expect(page.getByText("Executed", { exact: true })).toBeVisible();
      await shoot(page, "ExecutedRequest", theme);
    });

    test(`RequestsList ${theme}`, async ({ page }) => {
      await page.goto("/requests");
      await expect(page.getByTestId("requests-list")).toBeVisible();
      // All four seeded requests, in their various states.
      await expect(page.getByText(HERO_REQUEST_TITLE)).toBeVisible();
      await expect(page.getByText(PENDING_REQUEST_TITLE)).toBeVisible();
      await shoot(page, "RequestsList", theme);
    });

    test(`LiveSession ${theme}`, async ({ page }) => {
      await page.goto(`/requests/${seeded.liveSessionRequestId}/session`);
      const editor = page.getByTestId("monaco-editor-wrapper");
      await expect(editor).toBeVisible();
      // Let the websocket deliver the session's initial editor content —
      // typing before it lands gets wiped by the incoming sync.
      await page.waitForTimeout(1000);
      await editor.click();
      // The session editor content is synced server-side, so it may already
      // hold the query from a previous themed run — replace, don't append.
      // Monaco binds select-all to the platform's native chord.
      await page.keyboard.press(
        process.platform === "darwin" ? "Meta+A" : "Control+A",
      );
      await page.keyboard.press("Backspace");
      await page.keyboard.type(LIVE_SESSION_QUERY);
      await page.getByTestId("run-query-button").click();
      await expect(
        page.getByTestId("result-table-cell").first(),
      ).toBeVisible();
      await shoot(page, "LiveSession", theme);
    });

    test(`CreateConnection ${theme}`, async ({ page }) => {
      await openConnectionModal(page);
      // The README screenshot shows the advanced options expanded.
      await page.getByTestId("advanced-options-button").click();
      await expect(page.getByTestId("connection-port")).toBeVisible();
      await shoot(page, "CreateConnection", theme);
    });

    test(`CreateConnectionIAM ${theme}`, async ({ page }) => {
      await openConnectionModal(page);
      await page.getByText("AWS IAM", { exact: true }).click();
      await expect(page.getByText("Role ARN")).toBeVisible();
      const modal = page
        .locator("form")
        .filter({ hasText: "Add a new connection" });
      await shoot(page, "CreateConnectionIAM", theme, modal);
    });

    test(`CreateRole ${theme}`, async ({ page }) => {
      await page.goto("/settings/roles");
      await page.getByTestId("roles-table-create-button").click();
      await page.waitForURL("**/settings/roles/new");

      await page.locator('input[name="name"]').fill("Readonly Role");
      await page
        .locator('input[name="description"]')
        .fill("This role provides access to our readonly connections");
      await page.getByTestId("role-user-policy-read").check();
      await page.getByTestId("role-role-policy-read").check();

      await page.getByTestId("role-add-connection-policy-button").click();
      const selectorInput = page.getByTestId("connection-selector-input");
      await selectorInput.fill("readonly-*");
      await selectorInput.press("Escape");
      // The live match count proves the selector works against the seeded
      // connections ("This selector matches 1 of 6 Connections.").
      await expect(page.getByText(/This selector matches/)).toBeVisible();

      for (const permission of [
        "execution_request_read",
        "execution_request_write",
        "execution_request_review",
      ]) {
        const checkbox = page.getByTestId(
          `role-connection-policy-0-${permission}`,
        );
        if ((await checkbox.count()) > 0 && !(await checkbox.isChecked())) {
          await checkbox.check();
        }
      }
      await shoot(page, "CreateRole", theme);
    });

    test(`ApiKeys ${theme}`, async ({ page }) => {
      await page.goto("/settings/api-keys");
      await expect(page.getByTestId("api-keys-table")).toBeVisible();
      await expect(page.getByText("CI Pipeline")).toBeVisible();
      await shoot(page, "ApiKeys", theme);
    });

    test(`PostgresProxy ${theme}`, async ({ page }) => {
      await page.goto(`/requests/${seeded.proxyRequestId}`);
      await expect(
        page.getByRole("heading", { name: PROXY_REQUEST_TITLE }),
      ).toBeVisible();
      await page.getByTestId("execution-options-dropdown").click();
      await page.getByText("Start Proxy", { exact: true }).click();
      await expect(page.getByText("Proxy session active")).toBeVisible();
      await shoot(page, "PostgresProxy", theme);
    });
  });
}

// The audit log accumulates a row for every execution, including the ones the
// LiveSession tests above perform through the UI. Shooting it after all other
// tests keeps the light and dark captures identical in content.
for (const theme of THEMES) {
  test.describe(`${theme} mode audit log`, () => {
    test(`Auditlog ${theme}`, async ({ page }) => {
      await useTheme(page, theme);
      // The UI executions above happened in real time, after the seeded clock
      // anchor — anchor slightly ahead of now so all rows render sensibly.
      await page.clock.setFixedTime(Date.now() + 60_000);
      await page.goto("/auditlog");
      await expect(
        page.getByText("UPDATE shipping SET tracking_number").first(),
      ).toBeVisible();
      await shoot(page, "Auditlog", theme);
    });
  });
}
