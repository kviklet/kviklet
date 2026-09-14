import { test, expect } from "@playwright/test";
import { LoginPage, SettingsPage } from "./pages";

/**
 * Deactivating and reactivating users. A deactivated user keeps their record
 * but can no longer sign in until an admin reactivates them.
 */

const RUN = Date.now();
const PASSWORD = "e2e-deactivation-password";
const ADMIN = { email: "admin@admin.com", password: "admin" };
const dev = {
  name: `Deactivation Dev ${RUN}`,
  email: `deactivation-dev-${RUN}@example.com`,
};

test.describe.configure({ mode: "serial" });

test("an admin can deactivate and reactivate a user", async ({ page }) => {
  test.setTimeout(120_000);
  const loginPage = new LoginPage(page);
  const settingsPage = new SettingsPage(page);

  await page.goto("/");
  await loginPage.login(ADMIN.email, ADMIN.password);
  await settingsPage.addUser(dev.name, dev.email, PASSWORD);

  // The admin cannot deactivate themselves.
  await expect(
    page.getByTestId(`user-${ADMIN.email}`).getByTestId("deactivate-user"),
  ).toBeDisabled();

  const devRow = page.getByTestId(`user-${dev.email}`);
  await devRow.getByTestId("deactivate-user").click();
  await page.getByRole("button", { name: "Confirm" }).click();

  // Deactivated users stay listed, marked and sorted below the active ones.
  await expect(devRow).toHaveAttribute("data-deactivated", "true");
  await expect(devRow.getByTestId("deactivated-badge")).toBeVisible();
  await expect(page.getByTestId(/^user-/).last()).toHaveAttribute(
    "data-deactivated",
    "true",
  );

  // The deactivated user is refused with an explanation.
  await loginPage.logout();
  await page.getByTestId("email-input").fill(dev.email);
  await page.getByTestId("password-input").fill(PASSWORD);
  await page.getByTestId("login-button").click();
  await expect(
    page.getByText(
      "Your Kviklet account has been deactivated. Contact your administrator.",
    ),
  ).toBeVisible();

  // Reactivation restores the same account.
  await loginPage.loginFresh(ADMIN.email, ADMIN.password);
  await settingsPage.navigateToUsers();
  await devRow.getByTestId("reactivate-user").click();
  await expect(devRow).not.toHaveAttribute("data-deactivated", "true");
  await expect(devRow.getByTestId("deactivate-user")).toBeEnabled();

  await loginPage.loginFresh(dev.email, PASSWORD);
  await expect(page.getByTestId("requests-list")).toBeVisible();
});
