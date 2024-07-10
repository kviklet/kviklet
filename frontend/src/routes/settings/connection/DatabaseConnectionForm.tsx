import { z } from "zod";
import {
  ConnectionPayload,
  DatabaseType,
  TestConnectionResponse,
  testConnection,
} from "../../../api/DatasourceApi";
import { UseFormHandleSubmit, useForm } from "react-hook-form";
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

const connectionFormSchema = z
  .object({
    displayName: z.string().min(3),
    description: z.string(),
    id: z.string().min(3).min(3),
    type: z.nativeEnum(DatabaseType),
    hostname: z.string().min(1),
    port: z.coerce.number(),
    username: z.string().min(1),
    password: z.string().min(1),
    databaseName: z.string(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
    }),
    additionalJDBCOptions: z.string(),
    maxExecutions: z.coerce.number().nullable(),
  })
  .transform((data) => ({ ...data, connectionType: "DATASOURCE" }));

type ConnectionForm = z.infer<typeof connectionFormSchema>;

const getJDBCOptionsPlaceholder = (type: DatabaseType) => {
  if (type === DatabaseType.POSTGRES) {
    return "?sslmode=require";
  }
  if (type === DatabaseType.MYSQL) {
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
    setValue("maxExecutions", 1);
  }, []);

  useEffect(() => {
    if (!touchedFields.port) {
      if (watchType === DatabaseType.POSTGRES) {
        setValue("port", 5432);
      }
      if (watchType === DatabaseType.MYSQL) {
        setValue("port", 3306);
      }
      if (watchType === DatabaseType.MSSQL) {
        setValue("port", 1433);
      }
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
              {...register("type")}
              className="block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3 
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500"
              defaultValue={DatabaseType.POSTGRES}
            >
              <option value={DatabaseType.POSTGRES}>Postgres</option>
              <option value={DatabaseType.MYSQL}>MySQL</option>
              <option value={DatabaseType.MSSQL}>MS SQL</option>
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
          <InputField
            id="username"
            label="Username"
            placeholder="Username"
            {...register("username")}
            error={errors.username?.message}
          />
          <InputField
            id="password"
            label="Password"
            placeholder="Password"
            type="password"
            {...register("password")}
            error={errors.password?.message}
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
            <Disclosure defaultOpen={false}>
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
                        placeholder="1"
                        tooltip="The maximum number of times each request can be executed after it has been approved, usually 1."
                        type="number"
                        {...register("maxExecutions")}
                        error={errors.maxExecutions?.message}
                      />
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
            >
              Create Connection
            </Button>
          </div>
        </div>
      </div>
    </form>
  );
}

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
    console.log("resetting response");
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
          data.displayName = `${baseDisplayName} - ${db}`;
          data.id = convertToAlphanumericDash(`${data.id}-${db}`);
          data.databaseName = db;
          await props.createConnection({ ...data });
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
