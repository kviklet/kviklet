import { describe, expect, it } from "vitest";
import { connectionResponseSchema } from "./DatasourceApi";

const datasourceConnection = {
  id: "pg-prod",
  displayName: "Postgres Prod",
  type: "POSTGRESQL",
  protocol: "POSTGRESQL",
  maxExecutions: null,
  authenticationType: "USER_PASSWORD",
  username: "kviklet",
  hostname: "db.internal",
  port: 5432,
  description: "Production database",
  databaseName: null,
  reviewConfig: {
    numTotalRequired: 1,
    roleRequirements: [{ roleId: "role-1", numRequired: 1 }],
  },
  additionalJDBCOptions: "?sslmode=require",
  dumpsEnabled: false,
  temporaryAccessEnabled: true,
  explainEnabled: false,
  dryRunEnabled: false,
  dryRunRequiresApproval: true,
  roleArn: null,
  maxTemporaryAccessDuration: null,
  storeResults: false,
  category: null,
  permissions: null,
};

const kubernetesConnection = {
  id: "k8s-prod",
  displayName: "Kubernetes Prod",
  description: "Production cluster",
  reviewConfig: { numTotalRequired: 2 },
  maxExecutions: 1,
  temporaryAccessEnabled: true,
  storeResults: false,
  category: "prod",
  kubernetesExecInitialWaitTimeoutSeconds: 5,
  kubernetesExecTimeoutMinutes: 60,
  permissions: ["datasource_connection:get"],
};

describe("connectionResponseSchema", () => {
  it("tags a datasource connection and keeps nulls as null", () => {
    const parsed = connectionResponseSchema.parse(datasourceConnection);

    expect(parsed).toEqual({ ...datasourceConnection, _type: "DATASOURCE" });
  });

  it("tags a kubernetes connection", () => {
    const parsed = connectionResponseSchema.parse(kubernetesConnection);

    expect(parsed).toEqual({ ...kubernetesConnection, _type: "KUBERNETES" });
  });

  it("rejects an unknown database type", () => {
    const result = connectionResponseSchema.safeParse({
      ...datasourceConnection,
      type: "ORACLE",
    });

    expect(result.success).toBe(false);
  });
});
