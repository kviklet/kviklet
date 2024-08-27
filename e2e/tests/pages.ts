import { test, expect, Page } from "@playwright/test";

class LoginPage {
  constructor(private page: Page) {}

  async login(email: string, password: string) {
    await this.page.getByTestId("email-input").fill(email);
    await this.page.getByTestId("password-input").fill(password);
    await this.page.getByTestId("login-button").click();
    console.log("Waiting for requests list");
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

  async createConnection(
    name: string,
    username: string,
    password: string,
    host: string,
    port: string
  ) {
    await this.page.getByTestId("add-connection-button").click();
    await this.page.getByTestId("connection-name").fill(name);
    await this.page.getByTestId("connection-username").fill(username);
    await this.page.getByTestId("connection-password").fill(password);
    await this.page.getByTestId("connection-hostname").fill(host);
    await this.page.getByTestId("connection-required-reviews").fill("0");
    await this.page.getByTestId("advanced-options-button").click();
    await this.page.getByTestId("connection-port").fill(port);
    await this.page.getByTestId("create-connection-button").click();
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

  async executeRequest() {
    await this.navigate();
    await this.page.getByTestId("run-query-button").click();
    await this.page.waitForSelector('[data-testid="result-component"]');
  }
}

export { LoginPage, SettingsPage, RequestsPage, RequestsReviewPage };
