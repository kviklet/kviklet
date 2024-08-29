import { test, expect, Page } from "@playwright/test";

class LoginPage {
  constructor(private page: Page) {}

  async login(email: string, password: string) {
    await this.page.getByTestId("email-input").fill(email);
    await this.page.getByTestId("password-input").fill(password);
    await this.page.getByTestId("login-button").click();
    await this.page.waitForURL("**/requests");
    await this.page.waitForSelector('[data-testid="requests-list"]');
  }
}

class SettingsPage {
  constructor(private page: Page) {}

  async navigate() {
    await this.page.getByTestId("settings-dropdown").click();
    await this.page.getByRole("link", { name: "Settings" }).click();
    await this.page.waitForURL("**/settings");
  }

  async navigateToConnections() {
    await this.navigate();
    await this.page.getByTestId("settings-connections").click();
    await this.page.waitForURL("**/settings/connections");
  }

  async navigateToUsers() {
    await this.navigate();
    await this.page.getByTestId("settings-users").click();
    await this.page.waitForURL("**/settings/users");
  }

  async addUser(name: string, email: string, password: string) {
    await this.navigateToUsers();
    await this.page.getByTestId("add-user-button").click();
    await this.page.getByTestId("name-input").fill(name);
    await this.page.getByTestId("email-input").fill(email);
    await this.page.getByTestId("password-input").fill(password);
    await this.page.getByTestId("create-user-button").click();
    await this.page.waitForSelector(`[data-testid="user-${email}"]`);
  }

  async addDeveloperRoleToUser(email: string) {
    await this.navigateToUsers();

    const userRow = this.page.getByTestId(`user-${email}`);
    const roleCombobox = userRow.getByTestId("role-combobox-button");
    await roleCombobox.click();
    await this.page.getByTestId("role-combobox-option-Developer Role").click();
    await roleCombobox.click();

    await expect(roleCombobox).toContainText("Developer", { timeout: 5000 });
  }

  async createConnection(
    name: string,
    type: string,
    username: string,
    password: string,
    host: string,
    port: string,
    database?: string,
    additionalOptions?: string,
    requiredReviews?: number
  ) {
    await this.page.getByTestId("add-connection-button").click();
    await this.page.getByTestId("connection-name").fill(name);
    await this.page.getByTestId("connection-type").selectOption(type);
    await this.page.getByTestId("connection-username").fill(username);
    await this.page.getByTestId("connection-password").fill(password);
    await this.page.getByTestId("connection-hostname").fill(host);
    await this.page
      .getByTestId("connection-required-reviews")
      .fill(requiredReviews?.toString() ?? "0");
    await this.page.getByTestId("advanced-options-button").click();
    await this.page.getByTestId("connection-port").fill(port);
    if (database) {
      await this.page.getByTestId("connection-database").fill(database);
    }
    if (additionalOptions) {
      await this.page
        .getByTestId("connection-additional-options")
        .fill(additionalOptions);
    }
    await this.page.getByTestId("create-connection-button").click();
    await this.page.waitForSelector(`[data-testid="connection-card-${name}"]`);
  }
}

class RequestsPage {
  constructor(private page: Page) {}

  async navigate() {
    await this.page.getByTestId("new-link").click();
  }

  async createRequest(
    connectionName: string,
    name: string,
    description: string,
    query: string
  ) {
    await this.navigate();
    await this.page.getByTestId(`query-button-${connectionName}`).click();
    await this.page.getByTestId("request-title").fill(name);
    await this.page.getByTestId("request-description").fill(description);
    await this.page.getByTestId("request-statement").fill(query);
    await this.page.getByTestId("submit-button").click();
  }

  async createSession(
    connectionName: string,
    name: string,
    description: string
  ) {
    await this.navigate();
    await this.page.getByTestId(`access-button-${connectionName}`).click();
    await this.page.getByTestId("request-title").fill(name);
    await this.page.getByTestId("request-description").fill(description);
    await this.page.getByTestId("submit-button").click();
  }
}

class RequestsReviewPage {
  constructor(private page: Page, private requestName: string) {}

  async navigate() {
    await this.page.getByTestId("requests-link").click();
    await this.page.getByTestId(`request-link-${this.requestName}`).click();
  }

  async approveRequest() {
    await this.navigate();
    await this.page.getByTestId("review-type-Approve").click();
    await this.page.getByTestId("submit-review-button").click();
  }

  async startLiveSession() {
    await this.navigate();
    await this.page.getByTestId("run-query-button").click();
    await this.page.waitForURL("**/session");
  }

  async executeRequest() {
    await this.navigate();
    await this.page.getByTestId("run-query-button").click();
    await this.page.waitForSelector('[data-testid="result-component"]');
  }
}

class LiveSessionPage {
  constructor(private page: Page) {}

  async executeQuery(query: string) {
    await this.page.waitForSelector('[data-testid="monaco-editor-wrapper"]');

    await this.page.click('[data-testid="monaco-editor-wrapper"]');

    await this.page.keyboard.press("Control+A");
    await this.page.keyboard.press("Backspace");
    await this.page.keyboard.type(query);

    await this.page.getByTestId("run-query-button").click();
    await this.page.waitForSelector('[data-testid="result-component"]');
  }
}

export {
  LoginPage,
  SettingsPage,
  RequestsPage,
  RequestsReviewPage,
  LiveSessionPage,
};
