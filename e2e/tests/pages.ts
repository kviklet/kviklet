import { test, expect, Page } from "@playwright/test";

class LoginPage {
  constructor(private page: Page) {}

  async login(email: string, password: string) {
    await this.page.getByTestId("email-input").fill(email);
    await this.page.getByTestId("password-input").fill(password);
    await this.page.getByTestId("login-button").click();
    // Login lands on "/", which renders the requests list for users that may see it.
    await this.page.waitForSelector('[data-testid="requests-list"]');
  }

  /** Login from any state: drops existing cookies and starts at the login page. */
  async loginFresh(email: string, password: string) {
    await this.page.context().clearCookies();
    await this.page.goto("/");
    await this.page.waitForURL(/login/);
    await this.login(email, password);
  }

  async logout() {
    await this.page.getByTestId("settings-dropdown").click();
    await this.page.getByRole("button", { name: "Logout" }).click();
    await this.page.waitForURL("**/login");
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

  async navigateToRoles() {
    await this.navigate();
    await this.page.getByTestId("settings-roles").click();
    await this.page.waitForURL("**/settings/roles");
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
    await this.toggleUserRole(email, "Developer", true);
  }

  /** Selects (or deselects) a role for a user via the role combobox on the users page. */
  async toggleUserRole(email: string, roleName: string, selected: boolean) {
    await this.navigateToUsers();

    const userRow = this.page.getByTestId(`user-${email}`);
    const roleCombobox = userRow.getByTestId("role-combobox-button");
    await expect(roleCombobox).toBeVisible();
    // The button text lists the selected roles; skip the click if the user is
    // already in the desired state (keeps reruns on a used instance stable).
    const currentRoles = (await roleCombobox.textContent()) ?? "";
    if (currentRoles.includes(roleName) === selected) {
      return;
    }
    await roleCombobox.click();
    await this.page.getByTestId(`role-combobox-option-${roleName}`).click();

    // Toggling a role triggers an async save; wait for it to land (the button
    // text reflects the saved roles) before closing the dropdown, otherwise the
    // close click can race with the update and the change is dropped.
    if (selected) {
      await expect(roleCombobox).toContainText(roleName, { timeout: 10000 });
    } else {
      await expect(roleCombobox).not.toContainText(roleName, {
        timeout: 10000,
      });
    }
    await roleCombobox.click();
  }

  /** Creates a user and assigns the given (already existing) roles to them. */
  async addUserWithRoles(
    name: string,
    email: string,
    password: string,
    roleNames: string[] = [],
  ) {
    await this.addUser(name, email, password);
    for (const roleName of roleNames) {
      await this.toggleUserRole(email, roleName, true);
    }
  }

  /**
   * Creates a role through the role form. Connection policies grant Read
   * implicitly; pass write/review to also check the respective boxes.
   */
  async createRole(params: {
    name: string;
    description: string;
    roleRead?: boolean;
    connectionPolicies?: {
      connectionId: string;
      write?: boolean;
      review?: boolean;
    }[];
  }) {
    await this.navigateToRoles();
    await this.page.getByTestId("roles-table-create-button").click();
    await this.page.waitForURL("**/settings/roles/new");

    await this.page.locator('input[name="name"]').fill(params.name);
    await this.page
      .locator('input[name="description"]')
      .fill(params.description);
    if (params.roleRead) {
      await this.page.getByTestId("role-role-policy-read").check();
    }

    const policies = params.connectionPolicies ?? [];
    for (let index = 0; index < policies.length; index++) {
      const policy = policies[index];
      await this.page.getByTestId("role-add-connection-policy-button").click();
      const selectorInput = this.page
        .getByTestId("connection-selector-input")
        .nth(index);
      await selectorInput.fill(policy.connectionId);
      // Close the combobox suggestions so they don't cover the checkboxes.
      await selectorInput.press("Escape");
      if (policy.write) {
        await this.page
          .getByTestId(
            `role-connection-policy-${index}-execution_request_write`,
          )
          .check();
      }
      if (policy.review) {
        await this.page
          .getByTestId(
            `role-connection-policy-${index}-execution_request_review`,
          )
          .check();
      }
    }

    await this.page.getByTestId("role-submit-button").click();
    await this.page.waitForURL("**/settings/roles");
    await this.page.waitForSelector(`text=${params.name}`);
  }

  /** Reads a connection's id off its row on the connections settings page. */
  async getConnectionId(displayName: string): Promise<string> {
    const row = this.page
      .locator('[data-testid^="connections-table-row-"]')
      .filter({ hasText: displayName })
      .first();
    const testId = await row.getAttribute("data-testid");
    if (!testId) {
      throw new Error(`connection row for ${displayName} not found`);
    }
    return testId.replace("connections-table-row-", "");
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
    requiredReviews?: number,
  ) {
    // Click the Add Connection button (now in the header)
    await this.page.getByTestId("connections-table-create-button").click();

    // Select Database Connection from the modal
    await this.page.getByTestId("add-database-connection-button").click();

    // Fill in the connection details
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

    // Wait for the connection to appear in the table (table structure changed)
    await this.page.waitForSelector(`text=${name}`, { timeout: 10000 });
  }

  async createKubernetesConnection(params: {
    name: string;
    id: string;
    description?: string;
    requiredReviews?: number;
    maxExecutions?: number;
    initialWaitTimeoutSeconds?: number;
    timeoutMinutes?: number;
  }) {
    await this.page
      .getByTestId("connections-table-create-button")
      .first()
      .click();
    await this.page.getByTestId("add-kubernetes-connection-button").click();

    await this.page.getByTestId("kubernetes-connection-name").fill(params.name);
    await this.page
      .getByTestId("kubernetes-connection-description")
      .fill(params.description ?? "");
    await this.page.getByTestId("kubernetes-connection-id").fill(params.id);
    await this.page
      .getByTestId("kubernetes-connection-required-reviews")
      .fill((params.requiredReviews ?? 1).toString());
    await this.page.getByTestId("advanced-options-button").click();
    await this.page
      .getByTestId("kubernetes-connection-max-executions")
      .fill((params.maxExecutions ?? 1).toString());

    if (params.initialWaitTimeoutSeconds !== undefined) {
      await this.page
        .getByTestId("kubernetes-exec-initial-wait-timeout-seconds")
        .fill(params.initialWaitTimeoutSeconds.toString());
    }
    if (params.timeoutMinutes !== undefined) {
      await this.page
        .getByTestId("kubernetes-exec-timeout-minutes")
        .fill(params.timeoutMinutes.toString());
    }

    await this.page.getByTestId("create-kubernetes-connection-button").click();

    await this.page.waitForSelector(`text=${params.name}`, { timeout: 10000 });
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
    query: string,
  ) {
    await this.navigate();
    await this.page.getByTestId(`query-button-${connectionName}`).click();
    await this.page.getByTestId("request-title").fill(name);
    await this.page.getByTestId("request-description").fill(description);
    await this.page.getByTestId("request-statement").fill(query);
    await this.page.getByTestId("submit-button").click();
    // Submitting navigates to the new request's detail page; wait for it so
    // back-to-back creates don't race the navigation.
    await this.page.waitForURL("**/requests/*");
  }

  async createSession(
    connectionName: string,
    name: string,
    description: string,
  ) {
    await this.navigate();
    await this.page.getByTestId(`access-button-${connectionName}`).click();
    await this.page.getByTestId("request-title").fill(name);
    await this.page.getByTestId("request-description").fill(description);
    await this.page.getByTestId("submit-button").click();
  }
}

class RequestsReviewPage {
  constructor(
    private page: Page,
    private requestName: string,
  ) {}

  async navigate() {
    await this.page.getByTestId("requests-link").click();
    // Retried create tests can leave duplicate requests with the same title
    await this.page
      .getByTestId(`request-link-${this.requestName}`)
      .first()
      .click();
  }

  async approveRequest() {
    await this.navigate();
    await this.page.getByTestId("expand-comment-box").click();
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
