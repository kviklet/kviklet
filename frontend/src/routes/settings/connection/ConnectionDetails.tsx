import { useParams } from "react-router-dom";
import { z } from "zod";
import {
  DatabaseConnectionResponse,
  DatabaseType,
  KubernetesConnectionResponse,
} from "../../../api/DatasourceApi";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useConnection } from "../../../hooks/connections";
import Spinner from "../../../components/Spinner";
import InputField, { TextField } from "../../../components/InputField";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import { getJDBCOptionsPlaceholder } from "./DatabaseConnectionForm";
import Button from "../../../components/Button";

interface RequestReviewParams {
  connectionId: string;
}

const datasourceConnectionFormSchema = z
  .object({
    displayName: z.string().min(3),
    description: z.string(),
    type: z.nativeEnum(DatabaseType),
    hostname: z.string().min(1),
    port: z.coerce.number(),
    username: z.string().min(1),
    password: z.string(),
    databaseName: z.string(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
    }),
    additionalJDBCOptions: z.string(),
  })
  .transform((data) => ({ ...data, connectionType: "DATASOURCE" }));

type ConnectionForm = z.infer<typeof datasourceConnectionFormSchema>;

export default function ConnectionDetails() {
  const params = useParams() as unknown as RequestReviewParams;
  const connectionId = params.connectionId;

  const { loading, connection, editConnection } = useConnection(connectionId);

  return (
    <div>
      <div className="flex flex-col w-full">
        <div className="flex items-center justify-between w-full">
          <div className="text-lg font-semibold dark:text-white">
            Connection Settings
          </div>
        </div>
        {loading ? (
          <Spinner></Spinner>
        ) : (
          (connection && connection._type === "DATASOURCE" && (
            <UpdateDatasourceConnectionForm
              connection={connection}
              editConnection={editConnection}
            ></UpdateDatasourceConnectionForm>
          )) ||
          (connection && connection._type === "KUBERNETES" && (
            <UpdateKubernetesConnectionForm
              connection={connection}
              editConnection={editConnection}
            ></UpdateKubernetesConnectionForm>
          ))
        )}
      </div>
    </div>
  );
}

interface UpdateDatasourceFormProps {
  connection: DatabaseConnectionResponse;
  editConnection: (connection: ConnectionForm) => Promise<void>;
}

function UpdateDatasourceConnectionForm({
  connection,
  editConnection,
}: UpdateDatasourceFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
    watch,
  } = useForm<ConnectionForm>({
    resolver: zodResolver(datasourceConnectionFormSchema),
    defaultValues: {
      displayName: connection.displayName,
      description: connection.description,
      type: connection.type,
      hostname: connection.hostname,
      port: connection.port,
      username: connection.username,
      password: "",
      databaseName: connection.databaseName || "",
      reviewConfig: {
        numTotalRequired: connection.reviewConfig.numTotalRequired,
      },
      additionalJDBCOptions: connection.additionalJDBCOptions,
    },
  });

  const onSubmit = async (data: ConnectionForm) => {
    await editConnection(data);
  };

  const watchType = watch("type");

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)}>
      <div className="flex flex-col w-full">
        <div className="flex-col space-y-2">
          <div className="flex justify-between w-full">
            <label
              htmlFor="type"
              className="my-auto text-sm font-medium text-slate-700 dark:text-slate-200 mr-auto"
            >
              Database Type
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

const kubernetesConnectionFormSchema = z
  .object({
    displayName: z.string().min(3),
    description: z.string(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
    }),
  })
  .transform((data) => ({ ...data, connectionType: "KUBERNETES" }));
type KubernetesConnectionForm = z.infer<typeof kubernetesConnectionFormSchema>;

interface UpdateFormProps {
  connection: KubernetesConnectionResponse;
  editConnection: (connection: KubernetesConnectionForm) => Promise<void>;
}

function UpdateKubernetesConnectionForm({
  connection,
  editConnection,
}: UpdateFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors, isDirty },
  } = useForm<KubernetesConnectionForm>({
    resolver: zodResolver(kubernetesConnectionFormSchema),
    defaultValues: {
      displayName: connection.displayName,
      description: connection.description,
      reviewConfig: {
        numTotalRequired: connection.reviewConfig.numTotalRequired,
      },
    },
  });

  const onSubmit = async (data: KubernetesConnectionForm) => {
    await editConnection(data);
  };
  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)}>
      <div className="flex flex-col w-full ">
        <div className="flex-col space-y-2">
          <InputField
            label="Connection name"
            id="displayName"
            placeholder="Connection name"
            {...register("displayName")}
            error={errors.displayName?.message}
          />
          <TextField
            label="Description"
            id="description"
            placeholder="Provides prod read access with no required reviews"
            {...register("description")}
            error={errors.description?.message}
          />
          <InputField
            label="Required reviews"
            id="numTotalRequired"
            placeholder="1"
            type="number"
            {...register("reviewConfig.numTotalRequired")}
            error={errors.reviewConfig?.numTotalRequired?.message}
          />
          <Button
            type={isDirty ? "submit" : "disabled"}
            className="mt-4 btn btn-primary"
          >
            Save
          </Button>
        </div>
      </div>
    </form>
  );
}
