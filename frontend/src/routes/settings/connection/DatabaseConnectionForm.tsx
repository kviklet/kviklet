import { z } from "zod";
import {
  ConnectionPayload,
  DatabaseProtocol,
  DatabaseType,
  testConnection,
  TestConnectionResponse,
} from "../../../api/DatasourceApi";
import {
  FieldErrors,
  useForm,
  UseFormHandleSubmit,
  UseFormRegister,
  UseFormSetValue,
  UseFormWatch,
} from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import InputField, { TextField } from "../../../components/InputField";
import Button from "../../../components/Button";
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
import { isApiErrorResponse } from "../../../api/Errors";
import useNotification from "../../../hooks/useNotification";
import Spinner from "../../../components/Spinner";

const supportsIamAuth = (type: DatabaseType) =>
  [DatabaseType.POSTGRES, DatabaseType.MYSQL].includes(type);

const baseConnectionSchema = z.object({
  displayName: z.string().min(3),
  description: z.string(),
  id: z.string().min(3),
  type: z.nativeEnum(DatabaseType),
  protocol: z.nativeEnum(DatabaseProtocol),
  hostname: z.string().min(1),
  port: z.coerce.number(),
  databaseName: z.string(),
  reviewConfig: z.object({
    numTotalRequired: z.coerce.number(),
  }),
  additionalJDBCOptions: z.string(),
  maxExecutions: z.coerce.number().nullable(),
  dumpsEnabled: z.boolean(),
  connectionType: z.literal("DATASOURCE").default("DATASOURCE"),
});

const connectionFormSchema = z.discriminatedUnion("authenticationType", [
  baseConnectionSchema.extend({
    authenticationType: z.literal("USER_PASSWORD"),
    username: z.string().min(0),
    password: z.string().min(0),
  }),
  baseConnectionSchema.extend({
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
//type IamAuthFormType = Extract<ConnectionForm, { authenticationType: "aws-iam" }>;

const getJDBCOptionsPlaceholder = (type: DatabaseType) => {
  if (type === DatabaseType.POSTGRES) {
    return "?sslmode=require";
  }
  if (type === DatabaseType.MYSQL) {
    return "?useSSL=false";
  }
  if (type === DatabaseType.MARIADB) {
    return "?useSSL=false";
  }
  if (type === DatabaseType.MSSQL) {
    return ";encrypt=true;trustServerCertificate=true";
  }
  return "";
};

function convertToAlphanumericDash(input: string): string {
  const lowercased = input.toLowerCase();
  const converted = lowercased.replace(/[^a-zA-Z0-9-]+/g, "-");
  return converted.replace(/^-+|-+$/g, "");
}

export default function DatabaseConnectionForm(props: {
  createConnection: (payload: ConnectionPayload) => Promise<void>;
  closeModal: () => void;
}) {
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
    if (type === DatabaseType.MARIADB) {
      return [DatabaseProtocol.MARIADB];
    }
    if (type === DatabaseType.MONGODB) {
      return [DatabaseProtocol.MONGODB, DatabaseProtocol.MONGODB_SRV];
    }
    return [];
  };

  const {
    register,
    handleSubmit,
    formState: { errors, touchedFields },
    watch,
    resetField,
    setValue,
  } = useForm<ConnectionForm>({
    resolver: zodResolver(connectionFormSchema),
  });

  console.log(errors);

  const [protocolOptions, setProtocolOptions] = useState<DatabaseProtocol[]>([
    DatabaseProtocol.POSTGRESQL,
  ]);
  const [dumpsEnabledVisible, setDumpsEnabledVisible] = useState(false);

  const watchDisplayName = watch("displayName");
  const watchId = watch("id");

  const watchType = watch("type");

  useEffect(() => {
    if (watchId === "") {
      resetField("id");
    }
  }, [watchId]);

  useEffect(() => {
    const converted = convertToAlphanumericDash(watchDisplayName || "");
    if (!touchedFields.id) {
      setValue("id", converted);
    }
  }, [watchDisplayName]);

  useEffect(() => {
    setValue("reviewConfig", { numTotalRequired: 1 });
    setValue("port", 5432);
    setValue("type", DatabaseType.POSTGRES);
    setValue("protocol", DatabaseProtocol.POSTGRESQL);
    setValue("authenticationType", "USER_PASSWORD");
    setValue("maxExecutions", 1);
    setValue("dumpsEnabled", false);
  }, []);

  const updatePortIfNotTouched = (port: number) => {
    if (!touchedFields.port) {
      setValue("port", port);
    }
  };

  const updateJDBCOptionsIfNotTouched = (options: string) => {
    if (!touchedFields.additionalJDBCOptions) {
      setValue("additionalJDBCOptions", options);
    }
  };

  const protocol = watch("protocol");

  useEffect(() => {
    if (watchType === DatabaseType.POSTGRES) {
      updatePortIfNotTouched(5432);
    }
    if (watchType === DatabaseType.MYSQL) {
      updatePortIfNotTouched(3306);
      updateJDBCOptionsIfNotTouched("?allowMultiQueries=true");
      setDumpsEnabledVisible(true);
    }
    if (watchType === DatabaseType.MARIADB) {
      updatePortIfNotTouched(3306);
      updateJDBCOptionsIfNotTouched("?allowMultiQueries=true");
    }
    if (watchType === DatabaseType.MSSQL) {
      updatePortIfNotTouched(1433);
    }
    if (watchType === DatabaseType.MONGODB) {
      updatePortIfNotTouched(27017);
    }
    const protocolChoices = getProtocolOptions(watchType);
    setProtocolOptions(protocolChoices);
    if (!protocolChoices.includes(protocol)) {
      setValue("protocol", protocolChoices[0]);
    }
  }, [watchType]);

  const onSubmit = async (data: ConnectionForm) => {
    await props.createConnection(data);
    props.closeModal();
  };

  const handleSubmitCreate = handleSubmit(onSubmit);

  return (
    <form>
      <div className="w-2xl flex flex-col rounded-lg border border-slate-300 bg-slate-50 p-5 shadow dark:border-none dark:bg-slate-950">
        <h1 className="text-lg font-semibold">Add a new connection</h1>
        <div className="flex-col space-y-2">
          <div className="flex w-full justify-between">
            <label
              htmlFor="type"
              className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
            >
              Database Type
            </label>
            <select
              data-testid="connection-type"
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
              htmlFor="type"
              className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
            >
              Database Protocol
            </label>
            <select
              {...register("protocol")}
              className="block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500"
              defaultValue={protocolOptions[0]}
              disabled={protocolOptions.length === 1}
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
            data-testid="connection-name"
          />
          <TextField
            id="description"
            label="Description"
            placeholder="Provides prod read access with no required reviews"
            {...register("description")}
            error={errors.description?.message}
            data-testid="connection-description"
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
            data-testid="connection-hostname"
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
            data-testid="connection-required-reviews"
          />

          <div className="w-full">
            <Disclosure defaultOpen={false}>
              {({ open }) => (
                <>
                  <DisclosureButton
                    className="py-2"
                    data-testid="advanced-options-button"
                  >
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
                        id="id"
                        label="Connection ID"
                        placeholder="datasource-id"
                        {...register("id")}
                        error={errors.id?.message}
                      />
                      <InputField
                        id="databaseName"
                        label="Database name"
                        placeholder="Database name"
                        {...register("databaseName")}
                        error={errors.databaseName?.message}
                        data-testid="connection-database"
                      />
                      <InputField
                        id="port"
                        label="Port"
                        placeholder="Port"
                        type="number"
                        {...register("port")}
                        error={errors.port?.message}
                        data-testid="connection-port"
                      />
                      <InputField
                        id="additionalJDBCOptions"
                        label="Additional options"
                        data-testid="connection-additional-options"
                        placeholder={getJDBCOptionsPlaceholder(watchType)}
                        {...register("additionalJDBCOptions")}
                        error={errors.additionalJDBCOptions?.message}
                      />
                      <InputField
                        id="maxExecutions"
                        label="Max executions"
                        placeholder="1"
                        tooltip="The maximum number of times each request can be executed after it has been approved, usually 1."
                        type="number"
                        {...register("maxExecutions")}
                        error={errors.maxExecutions?.message}
                      />
                      {dumpsEnabledVisible && (
                        <div className="flex w-full justify-between">
                          <label
                            htmlFor="dumpsEnabled"
                            className="my-auto mr-auto flex items-center text-sm font-medium text-slate-700 dark:text-slate-200"
                          >
                            Dumps Enabled
                          </label>
                          <input
                            type="checkbox"
                            className="my-auto h-4 w-4"
                            {...register("dumpsEnabled")}
                          />
                        </div>
                      )}
                      <TestingConnectionFragment
                        handleSubmit={handleSubmit}
                        type={watchType}
                        createConnection={props.createConnection}
                        closeModal={props.closeModal}
                      />
                    </div>
                  </DisclosurePanel>
                </>
              )}
            </Disclosure>
          </div>
          <div className="flex">
            <Button
              type="submit"
              className="ml-auto"
              onClick={(event) => void handleSubmitCreate(event)}
              dataTestId="create-connection-button"
            >
              Create Connection
            </Button>
          </div>
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
  const supportsIamAuth = [DatabaseType.POSTGRES, DatabaseType.MYSQL].includes(
    databaseType,
  );

  useEffect(() => {
    if (!supportsIamAuth && authenticationType === "AWS_IAM") {
      setValue("authenticationType", "USER_PASSWORD");
    }
  }, [databaseType, authenticationType, supportsIamAuth, setValue]);

  const authenticationTypes = supportsIamAuth
    ? [
        { value: "USER_PASSWORD", label: "Username & Password" },
        { value: "AWS_IAM", label: "AWS IAM" },
      ]
    : [{ value: "USER_PASSWORD", label: "Username & Password" }];

  return (
    <div className="space-y-4">
      <div className="-mx-5 space-y-2 border-y border-slate-300 px-4 py-2 dark:border-slate-700 ">
        {/* Only show auth method selector if IAM is supported */}
        {supportsIamAuth && (
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
            placeholder="Password"
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

const TestingConnectionFragment = (props: {
  handleSubmit: UseFormHandleSubmit<ConnectionForm>;
  type: DatabaseType;
  createConnection: (connection: ConnectionPayload) => Promise<void>;
  closeModal: () => void;
}) => {
  const [isTestingConnection, setIsTestingConnection] = useState(false);
  const [testConnectionResponse, setTestConnectionResponse] =
    useState<TestConnectionResponse | null>(null);

  useEffect(() => {
    setTestConnectionResponse(null);
  }, [props.type]);

  const { addNotification } = useNotification();

  const handleSubmitTest = props.handleSubmit(async (data: ConnectionForm) => {
    setIsTestingConnection(true);
    const response = await testConnection(data);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to test connection",
        text: response.message,
        type: "error",
      });
    } else {
      setTestConnectionResponse(response);
    }
    setIsTestingConnection(false);
  });

  const handleSubmitCreateMultiple = props.handleSubmit(
    async (data: ConnectionForm) => {
      setIsTestingConnection(true);
      if (testConnectionResponse?.accessibleDatabases) {
        const baseDisplayName = data.displayName;
        for (const db of testConnectionResponse.accessibleDatabases) {
          await props.createConnection({
            ...data,
            displayName: `${baseDisplayName} - ${db}`,
            id: convertToAlphanumericDash(`${data.id}-${db}`),
            databaseName: db,
          });
        }
      } else {
        addNotification({
          title: "Failed to create connections",
          text: "No accessible databases found",
          type: "error",
        });
      }
      setIsTestingConnection(false);
      props.closeModal();
    },
  );

  return (
    <div className="flex-col">
      {isTestingConnection && <Spinner />}
      {!isTestingConnection && testConnectionResponse && (
        <div className="flex flex-col space-y-2">
          <div>
            <div className="text-sm font-semibold">Connection Status</div>
            <div>
              {testConnectionResponse.success ? (
                <div className="flex justify-between">
                  <span className="text-green-500">Success</span>
                  {testConnectionResponse?.accessibleDatabases &&
                    testConnectionResponse?.accessibleDatabases.length > 0 && (
                      <span
                        title={testConnectionResponse?.accessibleDatabases.join(
                          ", ",
                        )}
                        className="flex items-center"
                      >
                        {testConnectionResponse?.accessibleDatabases.length}{" "}
                        databases
                        <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400"></QuestionMarkCircleIcon>
                      </span>
                    )}
                </div>
              ) : (
                <span className="text-red-500">Failed</span>
              )}
            </div>
          </div>
          {!testConnectionResponse.success && (
            <div>
              <div className="text-sm font-semibold">Error</div>
              <div>{testConnectionResponse.details}</div>
            </div>
          )}
        </div>
      )}
      <div className="flex justify-between">
        <Button
          size="md"
          className="my-2"
          onClick={(event) => void handleSubmitTest(event)}
        >
          Test Connection
        </Button>
        {testConnectionResponse?.accessibleDatabases &&
          testConnectionResponse?.accessibleDatabases.length > 0 && (
            <Button
              size="md"
              textSize="sm"
              className="my-2"
              onClick={(event) => void handleSubmitCreateMultiple(event)}
            >
              Create For Each DB
            </Button>
          )}
      </div>
    </div>
  );
};

export { getJDBCOptionsPlaceholder };
