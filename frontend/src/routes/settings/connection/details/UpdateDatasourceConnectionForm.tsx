import { z } from "zod";
import {
  DatabaseConnectionResponse,
  DatabaseProtocol,
  DatabaseType,
} from "../../../../api/DatasourceApi";
import InputField, { TextField } from "../../../../components/InputField";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import { getJDBCOptionsPlaceholder } from "../DatabaseConnectionForm";
import Button from "../../../../components/Button";
import { useEffect, useState } from "react";
import { useConnectionForm } from "./ConnectionEditFormHook";

const datasourceConnectionFormSchema = z
  .object({
    displayName: z.string().min(3),
    description: z.string(),
    type: z.nativeEnum(DatabaseType),
    protocol: z.nativeEnum(DatabaseProtocol),
    hostname: z.string().min(1),
    port: z.coerce.number(),
    username: z.string(),
    password: z.string(),
    databaseName: z.string(),
    maxExecutions: z.coerce.number().nullable(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
      fourEyesRequired: z.boolean()
    }),
    additionalJDBCOptions: z.string(),
  })
  .transform((data) => ({ ...data, connectionType: "DATASOURCE" }));

type ConnectionForm = z.infer<typeof datasourceConnectionFormSchema>;

interface UpdateDatasourceFormProps {
  connection: DatabaseConnectionResponse;
  editConnection: (connection: ConnectionForm) => Promise<void>;
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
        fourEyesRequired: connection.reviewConfig.fourEyesRequired
      },
      additionalJDBCOptions: connection.additionalJDBCOptions || "",
      maxExecutions: connection.maxExecutions,
      connectionType: "DATASOURCE",
    },
    schema: datasourceConnectionFormSchema,
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
            placeholder="Unchanged"
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

          <InputField
              id="reviewConfig.fourEyesRequired"
              label="Four-eyes required"
              tooltip="Ensures that four-eyes (2 people) are required, where the editor cannot run the query themselvs."
              type="checkbox"
              {...register("reviewConfig.fourEyesRequired")}
              error={errors.reviewConfig?.fourEyesRequired?.message}
          />

          <div className="w-full">
            <Disclosure defaultOpen={true}>
              {({ open }) => (
                <>
                  <Disclosure.Button className="py-2">
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
                  </Disclosure.Button>
                  <Disclosure.Panel unmount={false}>
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
                    </div>
                  </Disclosure.Panel>
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
