import { APIRequestContext, request } from "@playwright/test";
import * as fs from "fs";
import * as path from "path";

/**
 * Seeds the demo data behind the README screenshots via the REST API.
 *
 * Everything here is idempotent: existing users/connections/requests/keys are
 * reused, so the spec can be re-run against a running e2e stack. For pristine
 * timestamps ("x minutes ago" instead of "3 days ago") regenerate against a
 * freshly started stack (docker compose down -v first).
 */

const ADMIN_EMAIL = "admin@admin.com";
const ADMIN_PASSWORD = "admin";
const ADMIN_NAME = "Jascha Beste";

const REVIEWER_EMAIL = "dan@example.com";
const REVIEWER_PASSWORD = "reviewerpass";
const REVIEWER_NAME = "Dan Nguyen";

// The test license allows exactly 2 users: the admin and the reviewer.
const LICENSE_PATH =
  process.env.KVIKLET_TEST_LICENSE_PATH ??
  path.resolve(__dirname, "../../../../license-script/test_license_key.json");

export const HERO_REQUEST_TITLE = "Update Tracking number of shipping 13";
export const HERO_STATEMENT =
  "UPDATE shipping SET tracking_number = 'TRACK1359' where shipping_id='13';";
export const PROXY_REQUEST_TITLE = "Investigate order sync delay";
export const LIVE_SESSION_REQUEST_TITLE = "Analyze shipping delays";
export const LIVE_SESSION_QUERY =
  "SELECT shipping_id, customer, tracking_number, status FROM shipping ORDER BY shipping_id;";
export const PENDING_REQUEST_TITLE = "Backfill missing order totals for July";

const CONNECTIONS: {
  id: string;
  displayName: string;
  description: string;
  numTotalRequired: number;
  maxExecutions?: number;
}[] = [
  {
    id: "root-connection",
    displayName: "Root Connection",
    description: "Provides full access to the prod database",
    numTotalRequired: 1,
    // Single execution so the hero request shows the "Executed" state.
    maxExecutions: 1,
  },
  {
    id: "readonly-connection",
    displayName: "Readonly Connection",
    description: "Provides readonly access with no required reviews",
    numTotalRequired: 0,
  },
  {
    id: "customer-tables",
    displayName: "Customer Tables",
    description: "Access to all tables of our customers db",
    numTotalRequired: 1,
  },
  {
    id: "order-tables",
    displayName: "Order Tables",
    description:
      "Access to our order tables, you can do everything here, but please be careful.",
    numTotalRequired: 1,
  },
  {
    id: "discounts",
    displayName: "Discounts",
    description: "Provides access to the discounts db",
    numTotalRequired: 1,
  },
  {
    id: "general-tables",
    displayName: "General Tables",
    description: "Provides general access to misc tables",
    numTotalRequired: 1,
  },
];

async function assertOk(
  response: { ok(): boolean; status(): number; text(): Promise<string> },
  what: string,
) {
  if (!response.ok()) {
    throw new Error(
      `${what} failed with ${response.status()}: ${await response.text()}`,
    );
  }
}

async function loginContext(
  baseURL: string,
  email: string,
  password: string,
): Promise<APIRequestContext> {
  const ctx = await request.newContext({
    baseURL,
    // CSRF protection: the backend rejects state-changing requests without it.
    extraHTTPHeaders: { "X-Kviklet-Request": "true" },
    // Explicitly empty: the spec's test.use storageState would otherwise be
    // inherited here, making admin and reviewer share one server session.
    storageState: { cookies: [], origins: [] },
  });
  const response = await ctx.post("/api/login", {
    data: { email, password },
  });
  await assertOk(response, `login as ${email}`);
  return ctx;
}

async function ensureLicense(admin: APIRequestContext) {
  const config = await admin.get("/api/config/");
  await assertOk(config, "fetch config");
  const configJson = (await config.json()) as { licenses?: unknown[] };
  if (configJson.licenses && configJson.licenses.length > 0) {
    return;
  }
  if (!fs.existsSync(LICENSE_PATH)) {
    throw new Error(
      `Test license not found at ${LICENSE_PATH}. ` +
        "Set KVIKLET_TEST_LICENSE_PATH to a valid license file " +
        "(needed for the API key and proxy screenshots).",
    );
  }
  const response = await admin.post("/api/config/license/", {
    multipart: {
      file: {
        name: "test_license_key.json",
        mimeType: "application/json",
        buffer: fs.readFileSync(LICENSE_PATH),
      },
    },
  });
  await assertOk(response, "upload license");
}

async function ensureUsers(admin: APIRequestContext) {
  const usersResponse = await admin.get("/api/users/");
  await assertOk(usersResponse, "list users");
  const { users } = (await usersResponse.json()) as {
    users: {
      id: string;
      email: string;
      fullName: string | null;
      roles: { id: string; name: string }[];
    }[];
  };

  const adminUser = users.find((u) => u.email === ADMIN_EMAIL);
  if (!adminUser) {
    throw new Error(`initial admin user ${ADMIN_EMAIL} not found`);
  }
  if (adminUser.fullName !== ADMIN_NAME) {
    const response = await admin.patch(`/api/users/${adminUser.id}`, {
      data: { fullName: ADMIN_NAME },
    });
    await assertOk(response, "rename admin user");
  }

  let reviewer = users.find((u) => u.email === REVIEWER_EMAIL);
  if (!reviewer) {
    const response = await admin.post("/api/users/", {
      data: {
        email: REVIEWER_EMAIL,
        password: REVIEWER_PASSWORD,
        fullName: REVIEWER_NAME,
      },
    });
    await assertOk(response, "create reviewer user");
    reviewer = (await response.json()) as typeof reviewer;
  }

  // The reviewer needs the Developer role to approve requests. Role updates
  // must always include the default role.
  if (!reviewer!.roles?.some((r) => r.name === "Developer")) {
    const rolesResponse = await admin.get("/api/roles/");
    await assertOk(rolesResponse, "list roles");
    const rolesJson = (await rolesResponse.json()) as {
      roles: { id: string; name: string }[];
    };
    const wanted = rolesJson.roles
      .filter((r) => r.name === "Default" || r.name === "Developer")
      .map((r) => r.id);
    const response = await admin.patch(`/api/users/${reviewer!.id}`, {
      data: { roles: wanted },
    });
    await assertOk(response, "assign Developer role to reviewer");
  }
}

async function ensureConnections(admin: APIRequestContext) {
  const listResponse = await admin.get("/api/connections/");
  await assertOk(listResponse, "list connections");
  const existing = (await listResponse.json()) as { id: string }[];
  const existingIds = new Set(existing.map((c) => c.id));

  for (const connection of CONNECTIONS) {
    if (existingIds.has(connection.id)) {
      continue;
    }
    const response = await admin.post("/api/connections/", {
      data: {
        connectionType: "DATASOURCE",
        id: connection.id,
        displayName: connection.displayName,
        description: connection.description,
        username: "postgres",
        password: "postgres",
        hostname: "postgres",
        port: 5432,
        databaseName: "postgres",
        type: "POSTGRESQL",
        reviewConfig: { numTotalRequired: connection.numTotalRequired },
        maxExecutions: connection.maxExecutions ?? null,
        temporaryAccessEnabled: true,
      },
    });
    await assertOk(response, `create connection ${connection.id}`);
  }
}

async function ensureProxyEnabled(admin: APIRequestContext) {
  const response = await admin.put("/api/config/", {
    data: { proxyEnabled: true },
  });
  await assertOk(response, "enable proxy in config");
}

type RequestSummary = {
  id: string;
  title: string;
  reviewStatus: string;
  executionStatus: string;
  createdAt: string;
};

async function findRequestByTitle(
  admin: APIRequestContext,
  title: string,
): Promise<RequestSummary | undefined> {
  const response = await admin.get("/api/execution-requests/");
  await assertOk(response, "list execution requests");
  const { requests } = (await response.json()) as {
    requests: RequestSummary[];
  };
  return requests.find((r) => r.title === title);
}

/**
 * The hero request: authored by the admin on a 1-review connection, approved
 * with a comment by the reviewer, then executed by the admin. The UPDATE runs
 * against the shipping table seeded in e2e/init.sql.
 */
async function ensureHeroRequest(
  admin: APIRequestContext,
  reviewer: APIRequestContext,
): Promise<string> {
  let summary = await findRequestByTitle(admin, HERO_REQUEST_TITLE);
  if (!summary) {
    const response = await admin.post("/api/execution-requests/", {
      data: {
        connectionType: "DATASOURCE",
        connectionId: "root-connection",
        title: HERO_REQUEST_TITLE,
        type: "SingleExecution",
        description:
          "The customer messaged us and reported a new delivery address. " +
          "So I manually started a new delivery process which has a new " +
          "tracking number. Because our admin portal is currently bugged " +
          "I quickly want to fix it this way.",
        statement: HERO_STATEMENT,
      },
    });
    await assertOk(response, "create hero request");
    const created = (await response.json()) as { id: string };
    summary = {
      id: created.id,
      title: HERO_REQUEST_TITLE,
      reviewStatus: "AWAITING_APPROVAL",
      executionStatus: "EXECUTABLE",
    };
  }

  if (summary.reviewStatus !== "APPROVED") {
    const response = await reviewer.post(
      `/api/execution-requests/${summary.id}/reviews`,
      { data: { comment: "Sure, looks good to me!", action: "APPROVE" } },
    );
    await assertOk(response, "approve hero request");
  }

  if (summary.executionStatus !== "EXECUTED") {
    const response = await admin.post(
      `/api/execution-requests/${summary.id}/execute`,
      { data: {} },
    );
    await assertOk(response, "execute hero request");
  }

  return summary.id;
}

/**
 * The proxy request: temporary access on the 0-review connection, so it is
 * approved immediately and the admin can start the proxy from the UI.
 */
async function ensureProxyRequest(admin: APIRequestContext): Promise<string> {
  const summary = await findRequestByTitle(admin, PROXY_REQUEST_TITLE);
  if (summary) {
    return summary.id;
  }
  const response = await admin.post("/api/execution-requests/", {
    data: {
      connectionType: "DATASOURCE",
      connectionId: "readonly-connection",
      title: PROXY_REQUEST_TITLE,
      type: "TemporaryAccess",
      description:
        "Need psql access to investigate why the nightly order sync is behind.",
      statement: null,
    },
  });
  await assertOk(response, "create proxy request");
  const created = (await response.json()) as { id: string };
  return created.id;
}

/**
 * The live session request: temporary access on the 0-review connection. It is
 * executed once here via the API so the audit log has content and the request
 * already shows its in-use state before any screenshot is taken.
 */
async function ensureLiveSessionRequest(
  admin: APIRequestContext,
): Promise<string> {
  let summary = await findRequestByTitle(admin, LIVE_SESSION_REQUEST_TITLE);
  if (!summary) {
    const response = await admin.post("/api/execution-requests/", {
      data: {
        connectionType: "DATASOURCE",
        connectionId: "readonly-connection",
        title: LIVE_SESSION_REQUEST_TITLE,
        type: "TemporaryAccess",
        description:
          "Several shipments are stuck in transit, looking into what they have in common.",
        statement: null,
      },
    });
    await assertOk(response, "create live session request");
    const created = (await response.json()) as { id: string };
    summary = {
      id: created.id,
      title: LIVE_SESSION_REQUEST_TITLE,
      reviewStatus: "APPROVED",
      executionStatus: "EXECUTABLE",
      createdAt: "",
    };
  }

  const detailResponse = await admin.get(
    `/api/execution-requests/${summary.id}`,
  );
  await assertOk(detailResponse, "fetch live session request detail");
  const detail = (await detailResponse.json()) as {
    events: { type: string }[];
  };
  if (!detail.events.some((e) => e.type === "EXECUTE")) {
    // A few varied statements so the audit log reads like a real session.
    const queries = [
      "SELECT status, count(*) FROM shipping GROUP BY status;",
      "SELECT * FROM shipping WHERE status = 'in_transit';",
      LIVE_SESSION_QUERY,
    ];
    for (const query of queries) {
      const response = await admin.post(
        `/api/execution-requests/${summary.id}/execute`,
        { data: { query } },
      );
      await assertOk(response, "execute live session query");
    }
  }

  return summary.id;
}

/** An untouched request awaiting approval, so the requests list shows variety. */
async function ensurePendingRequest(reviewer: APIRequestContext) {
  const summary = await findRequestByTitle(reviewer, PENDING_REQUEST_TITLE);
  if (summary) {
    return;
  }
  const response = await reviewer.post("/api/execution-requests/", {
    data: {
      connectionType: "DATASOURCE",
      connectionId: "order-tables",
      title: PENDING_REQUEST_TITLE,
      type: "SingleExecution",
      description:
        "The invoice importer skipped the totals column for orders created in July. " +
        "This recomputes them from the line items.",
      statement:
        "UPDATE orders SET total = subtotal + tax WHERE total IS NULL AND created_at >= '2026-07-01';",
    },
  });
  await assertOk(response, "create pending request");
}

async function ensureApiKeys(admin: APIRequestContext) {
  const listResponse = await admin.get("/api/api-keys/");
  await assertOk(listResponse, "list api keys");
  const { apiKeys } = (await listResponse.json()) as {
    apiKeys: { name: string }[];
  };
  const wanted = [
    { name: "CI Pipeline", expiresInDays: 90 },
    { name: "Audit Log Export", expiresInDays: 30 },
  ];
  for (const key of wanted) {
    if (apiKeys.some((k) => k.name === key.name)) {
      continue;
    }
    const response = await admin.post("/api/api-keys/", { data: key });
    await assertOk(response, `create api key ${key.name}`);
  }
}

export type SeededData = {
  heroRequestId: string;
  proxyRequestId: string;
  liveSessionRequestId: string;
  /**
   * A fixed point in time shortly after the demo data was created. The spec
   * pins the browser clock to it so relative timestamps ("2 minutes ago")
   * render identically on every run against the same stack.
   */
  clockAnchor: Date;
};

export async function seed(
  baseURL: string,
  adminStatePath: string,
): Promise<SeededData> {
  // The spec configures this file as storageState for all contexts — including,
  // through option inheritance, the API contexts created here — so it has to
  // exist before the first newContext call.
  if (!fs.existsSync(adminStatePath)) {
    fs.mkdirSync(path.dirname(adminStatePath), { recursive: true });
    fs.writeFileSync(
      adminStatePath,
      JSON.stringify({ cookies: [], origins: [] }),
    );
  }

  const admin = await loginContext(baseURL, ADMIN_EMAIL, ADMIN_PASSWORD);

  await ensureLicense(admin);
  await ensureUsers(admin);
  await ensureConnections(admin);
  await ensureProxyEnabled(admin);
  await ensureApiKeys(admin);

  const reviewer = await loginContext(
    baseURL,
    REVIEWER_EMAIL,
    REVIEWER_PASSWORD,
  );
  const heroRequestId = await ensureHeroRequest(admin, reviewer);
  const proxyRequestId = await ensureProxyRequest(admin);
  const liveSessionRequestId = await ensureLiveSessionRequest(admin);
  await ensurePendingRequest(reviewer);

  // Anchor the clock 5 minutes after the NEWEST seeded request, so no
  // relative timestamp ever renders negative (backend timestamps are UTC
  // without a zone marker).
  const listResponse = await admin.get("/api/execution-requests/");
  await assertOk(listResponse, "list execution requests for clock anchor");
  const { requests } = (await listResponse.json()) as {
    requests: RequestSummary[];
  };
  const newestCreatedAt = Math.max(
    ...requests.map((r) => {
      const utc = r.createdAt.includes("Z")
        ? r.createdAt
        : r.createdAt.replace(" ", "T") + "Z";
      return new Date(utc).getTime();
    }),
  );
  const clockAnchor = new Date(newestCreatedAt + 5 * 60 * 1000);

  // The browser contexts reuse the admin's session cookie so the UI tests
  // don't have to log in through the login form for every screenshot.
  fs.mkdirSync(path.dirname(adminStatePath), { recursive: true });
  fs.writeFileSync(
    adminStatePath,
    JSON.stringify(await admin.storageState(), null, 2),
  );

  await reviewer.dispose();
  await admin.dispose();

  return { heroRequestId, proxyRequestId, liveSessionRequestId, clockAnchor };
}
