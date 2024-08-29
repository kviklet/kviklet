import { test, expect, Page } from "@playwright/test";
import {
  LiveSessionPage,
  LoginPage,
  RequestsPage,
  RequestsReviewPage,
  SettingsPage,
} from "./pages";

test.describe("E2E Tests for Multiple Databases", () => {
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

  const databases = [
    {
      name: "PostgreSQL",
      type: "Postgres",
      username: "postgres",
      password: "postgres",
      host: "postgres",
      port: "5432",
      database: "postgres",
      testQuery: "SELECT 1 AS result; SELECT 2 AS result;",
      additionalOptions: undefined,
    },
    {
      name: "MySQL",
      type: "MySQL",
      username: "root",
      password: "root",
      host: "mysql",
      port: "3306",
      database: "mysql",
      testQuery: "SELECT 1 AS result; SELECT 2 AS result;",
      additionalOptions: undefined,
    },
    {
      name: "MSSQL",
      type: "MS SQL",
      username: "sa",
      password: "test1234TEST",
      host: "mssql",
      port: "1433",
      database: "master",
      testQuery: "SELECT 1 AS result; SELECT 2 AS result;",
      additionalOptions: ";encrypt=true;trustServerCertificate=true",
    },
    {
      name: "MariaDB",
      type: "MariaDB",
      username: "root",
      password: "root",
      host: "mariadb",
      port: "3306",
      database: "mysql",
      testQuery: "SELECT 1 AS result; SELECT 2 AS result;",
      dditionalOptions: undefined,
    },
    {
      name: "MongoDB",
      type: "MongoDB",
      username: "",
      password: "",
      host: "mongodb",
      port: "27017",
      database: "test",
      testQuery: "{ping: 1}",
      additionalOptions: undefined,
    },
  ];

  for (const db of databases) {
    test.describe(`${db.name} Tests`, () => {
      const connectionName = `${db.name} Test Connection`;
      const requestName = `${db.name} Test Query`;
      const liveSessionName = `${db.name} Live Session`;

      test(`Create ${db.name} Connection`, async ({ page }) => {
        await settingsPage.navigateToConnections();
        await settingsPage.createConnection(
          connectionName,
          db.type,
          db.username,
          db.password,
          db.host,
          db.port,
          db.database,
          db.additionalOptions
        );
        await expect(
          page.getByTestId(`connection-card-${connectionName}`)
        ).toHaveText(connectionName);
      });

      test(`Create ${db.name} Request`, async ({ page }) => {
        await requestsPage.createRequest(
          connectionName,
          requestName,
          `Testing ${db.name} connection`,
          db.testQuery
        );
        await page.waitForURL("**/requests");
        await expect(
          page.getByTestId(`request-link-${requestName}`)
        ).toBeVisible();
      });

      test(`Execute ${db.name} Request`, async ({ page }) => {
        const reviewPage = new RequestsReviewPage(page, requestName);
        await reviewPage.executeRequest();

        await validateResultOfQuery(db, page);
      });

      test(`Create ${db.name} Live Session`, async ({ page, context }) => {
        await requestsPage.createSession(
          connectionName,
          liveSessionName,
          `Testing ${db.name} live session`
        );
        await page.waitForURL("**/requests");
        await expect(
          page.getByTestId(`request-link-${liveSessionName}`)
        ).toBeVisible();
      });

      test(`Execute ${db.name} Live Session`, async ({ page }) => {
        const reviewPage = new RequestsReviewPage(page, liveSessionName);
        await reviewPage.startLiveSession();

        const liveSessionPage = new LiveSessionPage(page);
        await liveSessionPage.executeQuery(db.testQuery);

        await validateResultOfQuery(db, page);
      });
    });
  }
});

const validateResultOfQuery = async (db: { type: string }, page: Page) => {
  if (db.type !== "MongoDB") {
    const cellCount = await page.getByTestId("result-table-cell").count();
    expect(cellCount).toBe(1);
    const headerCount = await page.getByTestId("result-table-header").count();
    expect(headerCount).toBe(1);
  } else {
    await expect(page.getByTestId("result-component")).toContainText("ok: 1");
  }
};
