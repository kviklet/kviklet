import { ReactNode } from "react";
import { useFormContext } from "react-hook-form";
import {
  FormRow,
  FormSection,
  inputBaseClassName,
  inputClassName,
} from "../../../../components/FormSection";
import { DatabaseProtocol, DatabaseType } from "../../../../api/DatasourceApi";
import { supportsIamAuth } from "../../../../hooks/connections";
import {
  ConnectionFormInput,
  additionalOptionsPlaceholder,
  protocolOptions,
} from "./connectionForm";

const protocolLabels: Partial<Record<DatabaseProtocol, string>> = {
  [DatabaseProtocol.MONGODB]: "mongodb://",
  [DatabaseProtocol.MONGODB_SRV]: "mongodb+srv://",
};

export default function DatabaseSection(props: {
  /** Shown on an existing connection, where an empty password keeps the stored one. */
  passwordPlaceholder?: string;
  /** Rendered at the bottom of the section, e.g. the connection test. */
  children?: ReactNode;
}) {
  const {
    register,
    watch,
    formState: { errors },
  } = useFormContext<ConnectionFormInput>();
  const type = watch("type");
  const authenticationType = watch("authenticationType");
  const protocols = protocolOptions[type] ?? [];

  return (
    <FormSection title="Database" testId="connection-database-section">
      <FormRow
        label="Hostname and port"
        htmlFor="hostname"
        error={errors.hostname?.message ?? errors.port?.message}
      >
        <div className="flex gap-2">
          <input
            id="hostname"
            type="text"
            className={`${inputBaseClassName} min-w-0 flex-1`}
            placeholder="orders.db.internal"
            data-testid="connection-hostname"
            {...register("hostname")}
          />
          <input
            type="number"
            aria-label="Port"
            className={`${inputBaseClassName} w-24 shrink-0`}
            data-testid="connection-port"
            {...register("port")}
          />
        </div>
      </FormRow>
      {protocols.length > 1 && (
        <FormRow label="Protocol" htmlFor="protocol">
          <select
            id="protocol"
            className={inputClassName}
            data-testid="connection-protocol"
            {...register("protocol")}
          >
            {protocols.map((protocol) => (
              <option key={protocol} value={protocol}>
                {protocolLabels[protocol] ?? protocol}
              </option>
            ))}
          </select>
        </FormRow>
      )}
      <FormRow
        label="Database name"
        htmlFor="databaseName"
        hint="Optional. Leave empty to connect without selecting a database."
        error={errors.databaseName?.message}
      >
        <input
          id="databaseName"
          type="text"
          className={inputClassName}
          placeholder="orders"
          data-testid="connection-database"
          {...register("databaseName")}
        />
      </FormRow>
      {supportsIamAuth(type as DatabaseType) && (
        <FormRow
          label="Authentication"
          htmlFor="authenticationType"
          error={errors.authenticationType?.message}
        >
          <select
            id="authenticationType"
            className={inputClassName}
            data-testid="connection-authentication-type"
            {...register("authenticationType")}
          >
            <option value="USER_PASSWORD">Username and password</option>
            <option value="AWS_IAM">AWS IAM</option>
          </select>
        </FormRow>
      )}
      <FormRow
        label="Username"
        htmlFor="username"
        error={errors.username?.message}
      >
        <input
          id="username"
          type="text"
          className={inputClassName}
          autoComplete="off"
          data-testid="connection-username"
          {...register("username")}
        />
      </FormRow>
      {authenticationType === "USER_PASSWORD" && (
        <FormRow
          label="Password"
          htmlFor="password"
          error={errors.password?.message}
        >
          <input
            id="password"
            type="password"
            className={inputClassName}
            autoComplete="new-password"
            placeholder={props.passwordPlaceholder}
            data-testid="connection-password"
            {...register("password")}
          />
        </FormRow>
      )}
      {authenticationType === "AWS_IAM" && (
        <FormRow
          label="Role ARN"
          htmlFor="roleArn"
          hint="Optional. An IAM role Kviklet assumes before it requests the RDS token."
          error={errors.roleArn?.message}
        >
          <input
            id="roleArn"
            type="text"
            className={inputClassName}
            placeholder="arn:aws:iam::123456789012:role/MyRole"
            data-testid="connection-role-arn"
            {...register("roleArn")}
          />
        </FormRow>
      )}
      <FormRow
        label="Additional options"
        htmlFor="additionalJDBCOptions"
        hint="Optional. Appended to the connection string."
        error={errors.additionalJDBCOptions?.message}
      >
        <input
          id="additionalJDBCOptions"
          type="text"
          className={`${inputClassName} font-mono`}
          placeholder={additionalOptionsPlaceholder[type]}
          data-testid="connection-additional-options"
          {...register("additionalJDBCOptions")}
        />
      </FormRow>
      {props.children}
    </FormSection>
  );
}
