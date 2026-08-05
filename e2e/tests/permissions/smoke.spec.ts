import { test, expect } from "@playwright/test";
import {
  adminApi,
  apiLogin,
  createConnection,
  createRequest,
  createRestrictedUser,
  uiLogin,
} from "./helpers";

// Sanity check for the permission-test helpers: restricted user creation, connection
// and request setup, and browser login. Run before the per-cluster permission specs.
test("helpers can set up a restricted user and log them in", async ({
  page,
}) => {
  const api = await adminApi();
  const user = await createRestrictedUser(api, `smoke-${Date.now()}`, [
    { action: "datasource_connection:get", resource: "*" },
    { action: "execution_request:get", resource: "*" },
  ]);

  const connectionId = `smoke-conn-${Date.now()}`;
  await createConnection(api, connectionId, "Smoke Connection");
  const requestId = await createRequest(api, connectionId, "Smoke Request");
  expect(requestId).toBeTruthy();

  const userApi = await apiLogin(user.email, user.password);
  const status = await (await userApi.get("/api/status")).json();
  expect(status.permissions).toContain("execution_request:get");

  await uiLogin(page, user.email, user.password);
  // This user may see requests, so the index shows the requests page.
  await expect(page.getByTestId("requests-link")).toBeVisible();
});
