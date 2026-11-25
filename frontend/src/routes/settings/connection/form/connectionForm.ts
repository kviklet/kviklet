import { z } from "zod";
import {
  ConnectionPayload,
  ConnectionResponse,
  PatchConnectionPayload,
  DatabaseProtocol,
  DatabaseType,
  roleRequirementSchema,
} from "../../../../api/DatasourceApi";
import { supportsIamAuth } from "../../../../hooks/connections";

// An empty number input means "not set", which z.coerce.number() alone would read as 0.
const optionalPositiveInt = z.preprocess(
  (value) =>
    value === "" || value === null || value === undefined ? null : value,
  z.coerce
    .number("Must be a number")
    .int("Must be a whole number")
    .min(1, "Must be at least 1")
    .nullable(),
);

const newConnectionId = z
  .string()
  .min(3, "At least 3 characters")
  .max(255, "At most 255 characters")
  .regex(/^[a-zA-Z0-9-]+$/, "Only letters, numbers and dashes")
  // /settings/connections/new is the create form, so that id could never be opened.
  .refine((id) => id !== "new", { error: '"new" is reserved' });

const buildConnectionFormSchema = (id: z.ZodType<string, string>) =>
  z
    .object({
      connectionType: z.enum(["DATASOURCE", "KUBERNETES"]),
      displayName: z.string().trim().min(3, "At least 3 characters"),
      id,
      description: z.string(),
      category: z.string().nullable().optional(),

      type: z.enum(DatabaseType),
      protocol: z.enum(DatabaseProtocol),
      hostname: z.string().trim(),
      port: z.coerce.number("Must be a number"),
      databaseName: z.string(),
      additionalJDBCOptions: z.string(),
      authenticationType: z.enum(["USER_PASSWORD", "AWS_IAM"]),
      username: z.string(),
      password: z.string(),
      roleArn: z.string().nullable().optional(),

      reviewConfig: z.object({
        numTotalRequired: z.coerce
          .number("Must be a number")
          .int("Must be a whole number")
          .min(0, "Can't be negative"),
        roleRequirements: z.array(roleRequirementSchema).optional(),
      }),

      maxExecutions: optionalPositiveInt,
      maxTemporaryAccessDuration: optionalPositiveInt,

      temporaryAccessEnabled: z.boolean(),
      dryRunEnabled: z.boolean(),
      dryRunRequiresApproval: z.boolean(),
      explainEnabled: z.boolean(),
      dumpsEnabled: z.boolean(),
      storeResults: z.boolean(),

      kubernetesExecInitialWaitTimeoutSeconds:
        z.coerce.number("Must be a number"),
      kubernetesExecTimeoutMinutes: z.coerce.number("Must be a number"),
    })
    .superRefine((values, ctx) => {
      if (values.connectionType === "DATASOURCE") {
        if (values.hostname.length === 0) {
          ctx.addIssue({
            code: "custom",
            path: ["hostname"],
            message: "Required",
          });
        }
        if (!Number.isInteger(values.port) || values.port < 1) {
          ctx.addIssue({ code: "custom", path: ["port"], message: "Required" });
        }
        if (
          values.authenticationType === "AWS_IAM" &&
          !supportsIamAuth(values.type)
        ) {
          ctx.addIssue({
            code: "custom",
            path: ["authenticationType"],
            message: `AWS IAM authentication is not supported for ${databaseTypeLabel(
              values.type,
            )}`,
          });
        }
      } else {
        for (const field of [
          "kubernetesExecInitialWaitTimeoutSeconds",
          "kubernetesExecTimeoutMinutes",
        ] as const) {
          if (!Number.isInteger(values[field]) || values[field] < 1) {
            ctx.addIssue({
              code: "custom",
              path: [field],
              message: "Must be at least 1",
            });
          }
        }
      }
    });

const newConnectionFormSchema = buildConnectionFormSchema(newConnectionId);
// An existing connection's id is fixed and predates today's rules, so it is not validated.
const editConnectionFormSchema = buildConnectionFormSchema(z.string());

type ConnectionFormInput = z.input<typeof newConnectionFormSchema>;
type ConnectionFormValues = z.output<typeof newConnectionFormSchema>;

/** What the type picker offers: every database type plus Kubernetes. */
type ConnectionKind = DatabaseType | "KUBERNETES";

const databaseTypes: { value: DatabaseType; label: string }[] = [
  { value: DatabaseType.POSTGRES, label: "Postgres" },
  { value: DatabaseType.MYSQL, label: "MySQL" },
  { value: DatabaseType.MARIADB, label: "MariaDB" },
  { value: DatabaseType.MSSQL, label: "MS SQL" },
  { value: DatabaseType.MONGODB, label: "MongoDB" },
];

function databaseTypeLabel(type: DatabaseType): string {
  return databaseTypes.find((t) => t.value === type)?.label ?? type;
}

const protocolOptions: Record<DatabaseType, DatabaseProtocol[]> = {
  [DatabaseType.POSTGRES]: [DatabaseProtocol.POSTGRESQL],
  [DatabaseType.MYSQL]: [DatabaseProtocol.MYSQL],
  [DatabaseType.MARIADB]: [DatabaseProtocol.MARIADB],
  [DatabaseType.MSSQL]: [DatabaseProtocol.MSSQL],
  [DatabaseType.MONGODB]: [
    DatabaseProtocol.MONGODB,
    DatabaseProtocol.MONGODB_SRV,
  ],
};

const defaultPorts: Record<DatabaseType, number> = {
  [DatabaseType.POSTGRES]: 5432,
  [DatabaseType.MYSQL]: 3306,
  [DatabaseType.MARIADB]: 3306,
  [DatabaseType.MSSQL]: 1433,
  [DatabaseType.MONGODB]: 27017,
};

// Kviklet runs multi-statement requests, which the MySQL family only accepts with this flag.
const defaultAdditionalOptions: Record<DatabaseType, string> = {
  [DatabaseType.POSTGRES]: "",
  [DatabaseType.MYSQL]: "?allowMultiQueries=true",
  [DatabaseType.MARIADB]: "?allowMultiQueries=true",
  [DatabaseType.MSSQL]: "",
  [DatabaseType.MONGODB]: "",
};

const additionalOptionsPlaceholder: Record<DatabaseType, string> = {
  [DatabaseType.POSTGRES]: "?sslmode=require",
  [DatabaseType.MYSQL]: "?useSSL=false",
  [DatabaseType.MARIADB]: "?useSSL=false",
  [DatabaseType.MSSQL]: ";encrypt=true;trustServerCertificate=true",
  [DatabaseType.MONGODB]: "",
};

/** Dumps run mysqldump, so only MySQL connections can offer them. */
function supportsDumps(type: DatabaseType): boolean {
  return type === DatabaseType.MYSQL;
}

/** Dry run is not available for MongoDB. */
function supportsDryRun(type: DatabaseType): boolean {
  return type !== DatabaseType.MONGODB;
}

function toConnectionId(input: string): string {
  return input
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function newConnectionDefaults(category?: string | null): ConnectionFormInput {
  return {
    connectionType: "DATASOURCE",
    displayName: "",
    id: "",
    description: "",
    category: category ?? null,
    type: DatabaseType.POSTGRES,
    protocol: DatabaseProtocol.POSTGRESQL,
    hostname: "",
    port: defaultPorts[DatabaseType.POSTGRES],
    databaseName: "",
    additionalJDBCOptions: "",
    authenticationType: "USER_PASSWORD",
    username: "",
    password: "",
    roleArn: "",
    reviewConfig: { numTotalRequired: 1 },
    maxExecutions: 1,
    maxTemporaryAccessDuration: "",
    temporaryAccessEnabled: true,
    dryRunEnabled: false,
    dryRunRequiresApproval: true,
    explainEnabled: false,
    dumpsEnabled: false,
    storeResults: false,
    kubernetesExecInitialWaitTimeoutSeconds: 5,
    kubernetesExecTimeoutMinutes: 60,
  };
}

function reviewConfigPayload(values: ConnectionFormValues) {
  const roleRequirements = values.reviewConfig.roleRequirements;
  return {
    numTotalRequired: values.reviewConfig.numTotalRequired,
    ...(roleRequirements &&
      roleRequirements.length > 0 && { roleRequirements }),
  };
}

function toCreatePayload(values: ConnectionFormValues): ConnectionPayload {
  const base = {
    displayName: values.displayName,
    id: values.id,
    description: values.description,
    category: values.category || null,
    reviewConfig: reviewConfigPayload(values),
    // 0 is the backend's value for "no limit".
    maxExecutions: values.maxExecutions ?? 0,
    storeResults: values.storeResults,
  };

  if (values.connectionType === "KUBERNETES") {
    return {
      ...base,
      connectionType: "KUBERNETES",
      kubernetesExecInitialWaitTimeoutSeconds:
        values.kubernetesExecInitialWaitTimeoutSeconds,
      kubernetesExecTimeoutMinutes: values.kubernetesExecTimeoutMinutes,
    };
  }

  const database = {
    ...base,
    connectionType: "DATASOURCE" as const,
    type: values.type,
    protocol: values.protocol,
    hostname: values.hostname,
    port: values.port,
    databaseName: values.databaseName,
    additionalJDBCOptions: values.additionalJDBCOptions,
    username: values.username,
    temporaryAccessEnabled: values.temporaryAccessEnabled,
    maxTemporaryAccessDuration: values.temporaryAccessEnabled
      ? values.maxTemporaryAccessDuration
      : null,
    dryRunEnabled: supportsDryRun(values.type) && values.dryRunEnabled,
    dryRunRequiresApproval: values.dryRunRequiresApproval,
    explainEnabled: values.explainEnabled,
    dumpsEnabled: supportsDumps(values.type) && values.dumpsEnabled,
  };

  if (values.authenticationType === "AWS_IAM") {
    return {
      ...database,
      authenticationType: "AWS_IAM",
      roleArn: values.roleArn || null,
    };
  }
  return {
    ...database,
    authenticationType: "USER_PASSWORD",
    password: values.password,
  };
}

/** The form state for editing an existing connection. */
function connectionToFormValues(
  connection: ConnectionResponse,
): ConnectionFormInput {
  const shared = {
    ...newConnectionDefaults(),
    id: connection.id,
    displayName: connection.displayName,
    description: connection.description,
    category: connection.category,
    reviewConfig: {
      numTotalRequired: connection.reviewConfig.numTotalRequired,
      roleRequirements: connection.reviewConfig.roleRequirements ?? undefined,
    },
    // 0 is the backend's value for "no limit", which the form shows as an empty field.
    maxExecutions: connection.maxExecutions || "",
    storeResults: connection.storeResults,
  };

  if (connection._type === "KUBERNETES") {
    return {
      ...shared,
      connectionType: "KUBERNETES",
      kubernetesExecInitialWaitTimeoutSeconds:
        connection.kubernetesExecInitialWaitTimeoutSeconds,
      kubernetesExecTimeoutMinutes: connection.kubernetesExecTimeoutMinutes,
    };
  }
  return {
    ...shared,
    connectionType: "DATASOURCE",
    type: connection.type,
    protocol: connection.protocol,
    hostname: connection.hostname,
    port: connection.port,
    databaseName: connection.databaseName ?? "",
    additionalJDBCOptions: connection.additionalJDBCOptions ?? "",
    authenticationType: connection.authenticationType,
    username: connection.username,
    password: "",
    roleArn: connection.roleArn ?? "",
    maxTemporaryAccessDuration: connection.maxTemporaryAccessDuration ?? "",
    temporaryAccessEnabled: connection.temporaryAccessEnabled,
    dryRunEnabled: connection.dryRunEnabled,
    dryRunRequiresApproval: connection.dryRunRequiresApproval,
    explainEnabled: connection.explainEnabled,
    dumpsEnabled: connection.dumpsEnabled,
  };
}

function toPatchPayload(values: ConnectionFormValues): PatchConnectionPayload {
  const base = {
    displayName: values.displayName,
    description: values.description,
    category: values.category || null,
    reviewConfig: reviewConfigPayload(values),
    // 0 is the backend's value for "no limit"; null would leave the old limit in place.
    maxExecutions: values.maxExecutions ?? 0,
    storeResults: values.storeResults,
  };

  if (values.connectionType === "KUBERNETES") {
    return {
      ...base,
      connectionType: "KUBERNETES",
      kubernetesExecInitialWaitTimeoutSeconds:
        values.kubernetesExecInitialWaitTimeoutSeconds,
      kubernetesExecTimeoutMinutes: values.kubernetesExecTimeoutMinutes,
    };
  }

  const database = {
    ...base,
    connectionType: "DATASOURCE" as const,
    type: values.type,
    protocol: values.protocol,
    hostname: values.hostname,
    port: values.port,
    databaseName: values.databaseName,
    additionalJDBCOptions: values.additionalJDBCOptions,
    username: values.username,
    temporaryAccessEnabled: values.temporaryAccessEnabled,
    maxTemporaryAccessDuration: values.maxTemporaryAccessDuration,
    dryRunEnabled: supportsDryRun(values.type) && values.dryRunEnabled,
    dryRunRequiresApproval: values.dryRunRequiresApproval,
    explainEnabled: values.explainEnabled,
    dumpsEnabled: supportsDumps(values.type) && values.dumpsEnabled,
  };

  if (values.authenticationType === "AWS_IAM") {
    return {
      ...database,
      authenticationType: "AWS_IAM",
      roleArn: values.roleArn || null,
    };
  }
  return {
    ...database,
    authenticationType: "USER_PASSWORD",
    // Empty means "keep the stored password".
    password: values.password === "" ? undefined : values.password,
  };
}

export {
  newConnectionFormSchema,
  editConnectionFormSchema,
  connectionToFormValues,
  toPatchPayload,
  databaseTypes,
  databaseTypeLabel,
  protocolOptions,
  defaultPorts,
  defaultAdditionalOptions,
  additionalOptionsPlaceholder,
  supportsDumps,
  supportsDryRun,
  toConnectionId,
  newConnectionDefaults,
  toCreatePayload,
};
export type { ConnectionFormInput, ConnectionFormValues, ConnectionKind };
