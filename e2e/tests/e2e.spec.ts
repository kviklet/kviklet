import { test, expect } from "@playwright/test";
import {
  LoginPage,
  RequestsPage,
  RequestsReviewPage,
  SettingsPage,
} from "./pages";

test.describe("E2E Tests", () => {
  let loginPage: LoginPage;
  let settingsPage: SettingsPage;
  let requestsPage: RequestsPage;

  test.beforeEach(async ({ page }) => {
    loginPage = new LoginPage(page);
    settingsPage = new SettingsPage(page);
    requestsPage = new RequestsPage(page);

    await page.goto("/");
    await loginPage.login("admin@admin.com", "admin");
  });

  test("Create Connection", async ({ page }) => {
    await settingsPage.navigateToConnections();
    await settingsPage.createConnection(
      "my test connection",
      "postgres",
      "postgres",
      "postgres",
      "5432"
    );
    await expect(
      page.getByTestId("connection-card-my test connection")
    ).toHaveText("my test connection");
  });

  test("Create Request", async ({ page }) => {
    await requestsPage.createRequest(
      "my test connection",
      "my test query",
      "Testing if everything works",
      "Select * from test;"
    );
    await page.waitForURL("**/requests");
    await expect(page.getByTestId("request-link-my test query")).toBeVisible();
  });

  test("Execute Request", async ({ page }) => {
    const reviewPage = new RequestsReviewPage(page, "my test query");
    await reviewPage.executeRequest();
    const cellCount = await page.getByTestId("result-table-cell").count();
    expect(cellCount).toBeGreaterThan(4);
    const headerCount = await page.getByTestId("result-table-header").count();
    expect(headerCount).toBe(2);
  });
});
