import { z } from "zod";
import {
  DatabaseConnectionResponse,
  DatabaseProtocol,
  DatabaseType,
  PatchConnectionPayload,
} from "../../../../api/DatasourceApi";
import InputField, { TextField } from "../../../../components/InputField";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
} from "@headlessui/react";
import {
  ChevronDownIcon,
  ChevronRightIcon,
  QuestionMarkCircleIcon,
} from "@heroicons/react/20/solid";
import { getJDBCOptionsPlaceholder } from "../DatabaseConnectionForm";
import Button from "../../../../components/Button";
import { useEffect, useState } from "react";
import { useConnectionForm } from "./ConnectionEditFormHook";
import {
  FieldErrors,
  UseFormRegister,
  UseFormSetValue,
  UseFormWatch,
} from "react-hook-form";
import { supportsIamAuth } from "../../../../hooks/connections";

const baseConnectionFormSchema = z.object({
  displayName: z.string().min(3),
  description: z.string(),
  type: z.nativeEnum(DatabaseType),
  protocol: z.nativeEnum(DatabaseProtocol),
  hostname: z.string().min(1),
  port: z.coerce.number(),
  databaseName: z.string(),
  maxExecutions: z.coerce.number().nullable(),
  reviewConfig: z.object({
    numTotalRequired: z.coerce.number(),
  }),
  additionalJDBCOptions: z.string(),
  dumpsEnabled: z.boolean(),
  temporaryAccessEnabled: z.boolean(),
  explainEnabled: z.boolean(),
  connectionType: z.literal("DATASOURCE").default("DATASOURCE"),
});

const connectionFormSchema = z.discriminatedUnion("authenticationType", [
  baseConnectionFormSchema.extend({
    authenticationType: z.literal("USER_PASSWORD"),
    username: z.string().min(0),
    password: z.string().min(0),
  }),
  baseConnectionFormSchema.extend({
    authenticationType: z.literal("AWS_IAM"),
    username: z.string().min(0),
    type: z.nativeEnum(DatabaseType).refine(
      (type) => supportsIamAuth(type),
      (type) => ({
        message: `AWS IAM authentication is not supported for ${type}`,
      }),
    ),
  }),
]);

type ConnectionForm = z.infer<typeof connectionFormSchema>;
type BasicAuthFormType = Extract<
  ConnectionForm,
  { authenticationType: "USER_PASSWORD" }
>;

interface UpdateDatasourceFormProps {
  connection: DatabaseConnectionResponse;
  editConnection: (connection: PatchConnectionPayload) => Promise<void>;
}

const getProtocolOptions = (type: DatabaseType) => {
  if (type === DatabaseType.POSTGRES) {
    return [DatabaseProtocol.POSTGRESQL];
  }
  if (type === DatabaseType.MYSQL) {
    return [DatabaseProtocol.MYSQL];
  }
  if (type === DatabaseType.MSSQL) {
    return [DatabaseProtocol.MSSQL];
  }
  if (type === DatabaseType.MONGODB) {
    return [DatabaseProtocol.MONGODB, DatabaseProtocol.MONGODB_SRV];
  }
  if (type === DatabaseType.MARIADB) {
    return [DatabaseProtocol.MARIADB];
  }
  return [];
};

export default function UpdateDatasourceConnectionForm({
  connection,
  editConnection,
}: UpdateDatasourceFormProps) {
  const [protocolOptions, setProtocolOptions] = useState<DatabaseProtocol[]>(
    getProtocolOptions(connection.type),
  );

  const {
    register,
    formState: { errors, isDirty },
    watch,
    setValue,
    handleFormSubmit,
  } = useConnectionForm({
    initialValues: {
      displayName: connection.displayName,
      description: connection.description,
      type: connection.type,
      protocol: connection.protocol,
      hostname: connection.hostname,
      port: connection.port,
      username: connection.username,
      password: "",
      databaseName: connection.databaseName || "",
      reviewConfig: {
        numTotalRequired: connection.reviewConfig.numTotalRequired,
      },
      additionalJDBCOptions: connection.additionalJDBCOptions || "",
      maxExecutions: connection.maxExecutions,
      connectionType: "DATASOURCE",
      authenticationType: connection.authenticationType,
      dumpsEnabled: connection.dumpsEnabled,
      temporaryAccessEnabled: connection.temporaryAccessEnabled,
      explainEnabled: connection.explainEnabled,
    },
    schema: connectionFormSchema,
    onSubmit: editConnection,
    connectionType: "DATASOURCE",
  });

  const watchType = watch("type");
  useEffect(() => {
    const protocolOptions = getProtocolOptions(watchType);
    if (!protocolOptions.includes(connection.protocol)) {
      setValue("protocol", protocolOptions[0]);
    }
    setProtocolOptions(protocolOptions);
  }, [watchType, connection.protocol, setValue]);

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        void handleFormSubmit();
      }}
    >
      <div className="flex w-full flex-col">
        <div className="flex-col space-y-2">
          <div className="flex w-full justify-between">
            <label
              htmlFor="type"
              className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
            >
              Database Type
            </label>
            <select
              {...register("type")}
              className="block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3 
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500"
              defaultValue={DatabaseType.POSTGRES}
            >
              <option value={DatabaseType.POSTGRES}>Postgres</option>
              <option value={DatabaseType.MYSQL}>MySQL</option>
              <option value={DatabaseType.MARIADB}>MariaDB</option>
              <option value={DatabaseType.MSSQL}>MS SQL</option>
              <option value={DatabaseType.MONGODB}>MongoDB</option>
            </select>
          </div>

          <div className="flex w-full justify-between">
            <label
              htmlFor="protocol"
              className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
            >
              Database Protocol
            </label>
            <select
              className="block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500"
              defaultValue={protocolOptions[0]}
              disabled={protocolOptions.length === 1}
              {...register("protocol")}
            >
              {protocolOptions.map((protocol) => (
                <option key={protocol} value={protocol}>
                  {protocol}
                </option>
              ))}
            </select>
          </div>

          <InputField
            id="displayName"
            label="Name"
            placeholder="Connection name"
            {...register("displayName")}
            error={errors.displayName?.message}
          />
          <TextField
            id="description"
            label="Description"
            placeholder="Provides prod read access with no required reviews"
            {...register("description")}
            error={errors.description?.message}
          />
          <AuthSection
            register={register}
            errors={errors}
            watch={watch}
            setValue={setValue}
          />
          <InputField
            id="hostname"
            label="Hostname"
            placeholder="localhost"
            {...register("hostname")}
            error={errors.hostname?.message}
          />
          <InputField
            id="reviewConfig.numTotalRequired"
            label="Required reviews"
            tooltip="The number of required approving reviews that's required before a request can be executed."
            placeholder="1"
            type="number"
            min="0"
            {...register("reviewConfig.numTotalRequired")}
            error={errors.reviewConfig?.numTotalRequired?.message}
          />

          <div className="w-full">
            <Disclosure defaultOpen={true}>
              {({ open }) => (
                <>
                  <DisclosureButton className="py-2">
                    <div className="flex flex-row justify-between">
                      <div className="flex flex-row">
                        <div>Advanced Options</div>
                      </div>
                      <div className="flex flex-row">
                        {open ? (
                          <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                        ) : (
                          <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                        )}
                      </div>
                    </div>
                  </DisclosureButton>
                  <DisclosurePanel unmount={false}>
                    <div className="flex-col space-y-2">
                      <InputField
                        id="databaseName"
                        label="Database name"
                        placeholder="Database name"
                        {...register("databaseName")}
                        error={errors.databaseName?.message}
                      />
                      <InputField
                        id="port"
                        label="Port"
                        placeholder="Port"
                        type="number"
                        {...register("port")}
                        error={errors.port?.message}
                      />
                      <InputField
                        id="additionalJDBCOptions"
                        label="Additional JDBC options"
                        placeholder={getJDBCOptionsPlaceholder(watchType)}
                        {...register("additionalJDBCOptions")}
                        error={errors.additionalJDBCOptions?.message}
                      />
                      <InputField
                        id="maxExecutions"
                        label="Max executions"
                        placeholder="Max executions"
                        tooltip="The maximum number of times each request can be executed after it has been approved, usually 1."
                        type="number"
                        {...register("maxExecutions")}
                        error={errors.maxExecutions?.message}
                      />
                      <div className="flex w-full justify-between">
                        <label
                          htmlFor="dumpsEnabled"
                          className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
                        >
                          Dumps Enabled
                        </label>
                        <input
                          type="checkbox"
                          className="my-auto h-4 w-4"
                          {...register("dumpsEnabled")}
                        />
                      </div>
                      <div className="flex w-full justify-between">
                        <label
                          htmlFor="temporaryAccessEnabled"
                          className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
                        >
                          Temporary Access Enabled
                        </label>
                        <input
                          type="checkbox"
                          className="my-auto h-4 w-4"
                          {...register("temporaryAccessEnabled")}
                        />
                      </div>
                      <div className="flex w-full justify-between">
                        <label
                          htmlFor="explainEnabled"
                          className="my-auto mr-auto flex items-center text-sm font-medium text-slate-700 dark:text-slate-200"
                          title="This feature relies on SQL parsing, it's recommended to only enable it on read-only connections."
                        >
                          Explain Enabled
                          <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400"></QuestionMarkCircleIcon>
                        </label>
                        <input
                          type="checkbox"
                          className="my-auto h-4 w-4"
                          {...register("explainEnabled")}
                        />
                      </div>
                    </div>
                  </DisclosurePanel>
                </>
              )}
            </Disclosure>
          </div>
          <Button type={isDirty ? "submit" : "disabled"}>Save</Button>
        </div>
      </div>
    </form>
  );
}

type AuthSectionProps = {
  register: UseFormRegister<ConnectionForm>;
  errors: FieldErrors<ConnectionForm>;
  watch: UseFormWatch<ConnectionForm>;
  setValue: UseFormSetValue<ConnectionForm>;
};

const AuthSection = ({
  register,
  errors,
  watch,
  setValue,
}: AuthSectionProps) => {
  const databaseType = watch("type");
  const authenticationType = watch("authenticationType");
  const iamAuthIsSupported = supportsIamAuth(databaseType);

  useEffect(() => {
    if (!iamAuthIsSupported && authenticationType === "AWS_IAM") {
      setValue("authenticationType", "USER_PASSWORD");
    }
  }, [databaseType, authenticationType, iamAuthIsSupported, setValue]);

  const authenticationTypes = iamAuthIsSupported
    ? [
        { value: "USER_PASSWORD", label: "Username & Password" },
        { value: "AWS_IAM", label: "AWS IAM" },
      ]
    : [{ value: "USER_PASSWORD", label: "Username & Password" }];

  return (
    <div className="space-y-4">
      <div className="-mx-5 space-y-2 border-y border-slate-300 px-4 py-2 dark:border-slate-700 ">
        {/* Only show auth method selector if IAM is supported */}
        {iamAuthIsSupported && (
          <fieldset className="relative flex items-center justify-between">
            <label className="my-auto mr-auto flex items-center text-sm font-medium text-slate-700 dark:text-slate-200">
              Authentication Method
            </label>
            <div className="flex basis-3/5 rounded-lg bg-slate-100 dark:bg-slate-900">
              {authenticationTypes.map((method) => (
                <label
                  key={method.value}
                  className={`flex flex-1 cursor-pointer items-center justify-center rounded-md px-3 py-2
                    text-center text-sm font-medium tracking-tighter transition-colors
                  ${
                    watch("authenticationType") === method.value
                      ? "bg-slate-400 text-slate-900 dark:bg-indigo-600 dark:text-slate-50"
                      : "text-slate-700 hover:bg-slate-200 dark:text-slate-300 hover:dark:bg-slate-800"
                  }`}
                >
                  <input
                    type="radio"
                    className="hidden"
                    {...register("authenticationType")}
                    value={method.value}
                  />
                  {method.label}
                </label>
              ))}
            </div>
          </fieldset>
        )}

        <InputField
          id="username"
          label="Username"
          placeholder="Username"
          {...register("username")}
          error={errors.username?.message}
          data-testid="connection-username"
        />
        {watch("authenticationType") === "USER_PASSWORD" && (
          <InputField
            id="password"
            label="Password"
            placeholder="Unchanged"
            type="password"
            {...register("password")}
            error={(errors as FieldErrors<BasicAuthFormType>).password?.message}
            data-testid="connection-password"
          />
        )}
      </div>
    </div>
  );
};
