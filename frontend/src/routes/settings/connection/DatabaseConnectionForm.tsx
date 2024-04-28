import { z } from "zod";
import { ConnectionPayload, DatabaseType } from "../../../api/DatasourceApi";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import InputField from "../../../components/InputField";
import Button from "../../../components/Button";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";

const connectionFormSchema = z
  .object({
    displayName: z.string().min(3),
    description: z.string(),
    id: z.string().min(3).min(3),
    type: z.nativeEnum(DatabaseType),
    hostname: z.string().min(1),
    port: z.number(),
    username: z.string().min(1),
    password: z.string().min(1),
    databaseName: z.string(),
    reviewConfig: z.object({
      numTotalRequired: z.number(),
    }),
    additionalJDBCOptions: z.string(),
  })
  .transform((data) => ({ ...data, connectionType: "DATASOURCE" }));

type ConnectionForm = z.infer<typeof connectionFormSchema>;

export default function DatabaseConnectionForm(props: {
  handleCreateConnection: (connection: ConnectionPayload) => Promise<void>;
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
    const lowerCasedString = watchDisplayName?.toLowerCase() || "";
    if (!touchedFields.id) {
      setValue("id", lowerCasedString.replace(/\s+/g, "-"));
    }
  }, [watchDisplayName]);

  useEffect(() => {
    setValue("reviewConfig", { numTotalRequired: 1 });
    setValue("port", 5432);
    setValue("type", DatabaseType.POSTGRES);
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

  const getJDBCOptionsPlaceholder = () => {
    if (watchType === DatabaseType.POSTGRES) {
      return "?sslmode=require";
    }
    if (watchType === DatabaseType.MYSQL) {
      return "?useSSL=false";
    }
    if (watchType === DatabaseType.MSSQL) {
      return ";encrypt=true;trustServerCertificate=true";
    }
    return "";
  };

  const onSubmit = async (data: ConnectionForm) => {
    await props.handleCreateConnection(data);
  };

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)}>
      <div className="flex flex-col w-2xl shadow p-5 bg-slate-50 border border-slate-300 dark:border-none dark:bg-slate-950 rounded-lg">
        <h1 className="text-lg font-semibold">Add a new connection</h1>
        <div className="flex-col space-y-2">
          <div className="flex justify-between w-full">
            <label
              htmlFor="type"
              className="my-auto text-sm font-medium text-slate-700 dark:text-slate-200 mr-auto"
            >
              Database type:
            </label>
            <select
              {...register("type")}
              className="basis-3/5 appearance-none block w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 text-sm transition-colors"
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
          <InputField
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
            placeholder="Hostname"
            {...register("hostname")}
            error={errors.hostname?.message}
          />
          <InputField
            id="reviewConfig.numTotalRequired"
            label="Required reviews"
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
                  <Disclosure.Panel>
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
                        placeholder={getJDBCOptionsPlaceholder()}
                        {...register("additionalJDBCOptions")}
                        error={errors.additionalJDBCOptions?.message}
                      />
                    </div>
                  </Disclosure.Panel>
                </>
              )}
            </Disclosure>
          </div>
          <Button type="submit">Create Connection</Button>
        </div>
      </div>
    </form>
  );
}
