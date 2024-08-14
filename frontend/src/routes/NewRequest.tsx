import { useEffect, useState } from "react";
import { ConnectionResponse, DatabaseType } from "../api/DatasourceApi";
import Spinner from "../components/Spinner";
import {
  ChevronDownIcon,
  ChevronRightIcon,
  CircleStackIcon,
  CloudIcon,
  CommandLineIcon,
} from "@heroicons/react/20/solid";
import { useLocation, useNavigate } from "react-router-dom";
import { SubmitHandler, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { addRequest } from "../api/ExecutionRequestApi";
import { z } from "zod";
import Button from "../components/Button";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
} from "@headlessui/react";
import { Pod, getPods } from "../api/KubernetesApi";
import useConnections from "../hooks/connections";
import useNotification from "../hooks/useNotification";
import { isApiErrorResponse } from "../api/Errors";
import SearchInput from "../components/SearchInput";

const languageString = (connection: ConnectionResponse): string => {
  if (connection._type === "DATASOURCE") {
    if (connection.type === DatabaseType.MONGODB) {
      return "MQL";
    } else {
      return "SQL";
    }
  } else {
    return "Command";
  }
};

const queryPlaceholder = (connection: ConnectionResponse): string => {
  if (connection._type === "DATASOURCE") {
    if (connection.type === DatabaseType.MONGODB) {
      return `{
    "find": "testCollection",
    "filter": { "name": "John Doe" }
}`;
    } else {
      return "SELECT id FROM some_table;";
    }
  } else {
    return "echo hello world";
  }
};

const DatasourceExecutionRequestSchema = z
  .object({
    connectionType: z.literal("DATASOURCE"),
    title: z.string().min(1, { message: "Title is required" }),
    type: z.enum(["TemporaryAccess", "SingleExecution", "SQLDump"]),
    description: z.string(),
    statement: z.string().optional(),
    connectionId: z.string().min(1),
  })
  .refine(
    (data) =>
      data.type === "TemporaryAccess" ||
      "SQLDump" ||
      (!!data.statement && data.type === "SingleExecution"),
    {
      message: "If you create a query request an SQL statement is rquired",
    },
  );

const KubernetesExecutionRequestSchema = z
  .object({
    connectionType: z.literal("KUBERNETES"),
    title: z.string().min(1, { message: "Title is required" }),
    type: z.enum(["TemporaryAccess", "SingleExecution"]),
    description: z.string(),
    command: z.string().optional(),
    connectionId: z.string().min(1),
    namespace: z.string().min(1).default("default"),
    podName: z.string().min(1),
    containerName: z.string().optional(),
  })
  .refine(
    (data) =>
      data.type === "TemporaryAccess" ||
      (!!data.command && data.type === "SingleExecution"),
    {
      message: "If you create a command request a command is required",
    },
  );

const ExecutionRequestSchema = z.union([
  DatasourceExecutionRequestSchema,
  KubernetesExecutionRequestSchema,
]);

type DatasourceExecutionRequest = z.infer<
  typeof DatasourceExecutionRequestSchema
>;
type KubernetesExecutionRequest = z.infer<
  typeof KubernetesExecutionRequestSchema
>;
type ExecutionRequest = z.infer<typeof ExecutionRequestSchema>;

interface PreConfiguredStateKubernetes {
  connectionId: string;
  mode: "SingleExecution" | "TemporaryAccess";
  connectionType: "Kubernetes";
  title: string;
  description: string;
  command: string;
  namespace: string;
  containerName: string;
  podName: string;
}

interface PreConfiguredStateDatasource {
  connectionId: string;
  mode: "SingleExecution" | "TemporaryAccess" | "SQLDump";
  connectionType: "Datasource";
  title: string;
  description: string;
  statement: string;
}

type PreConfiguredState =
  | PreConfiguredStateKubernetes
  | PreConfiguredStateDatasource;

export default function ConnectionChooser() {
  const { connections, loading } = useConnections();
  const [chosenConnection, setChosenConnection] = useState<
    ConnectionResponse | undefined
  >(undefined);
  const [chosenMode, setChosenMode] = useState<
    "SingleExecution" | "TemporaryAccess" | "SQLDump" | undefined
  >(undefined);
  const [searchTerm, setSearchTerm] = useState("");

  const filteredConnections = connections.filter(
    (connection) =>
      connection.displayName.toLowerCase().includes(searchTerm.toLowerCase()) ||
      connection.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
      connection.id.toLowerCase().includes(searchTerm.toLowerCase()),
  );

  const location = useLocation();

  useEffect(() => {
    const state = location.state as PreConfiguredState;

    if (state) {
      const connectionFromState = connections.find(
        (c) => c.id === state.connectionId,
      );
      setChosenConnection(connectionFromState);
      setChosenMode(state.mode);
    }
  }, [loading]);

  return (
    <div>
      <div className=" border-b border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-950">
        <h1 className=" m-5 mx-auto max-w-5xl pl-1.5 text-xl">
          {" "}
          Request Access to a Database
        </h1>
      </div>
      <div className="mx-auto mt-5 flex max-w-5xl">
        {loading ? (
          <Spinner></Spinner>
        ) : (
          <div className="w-full">
            <Disclosure defaultOpen={true}>
              {({ open, close }) => (
                <>
                  <DisclosureButton className="py-2">
                    <div className="flex flex-row justify-between">
                      <div className="flex flex-row">
                        <div>Connections</div>
                      </div>
                      <div className="flex flex-row">
                        {open ? (
                          <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                        ) : (
                          <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                        )}
                      </div>
                      {chosenConnection && (
                        <div className="flex flex-row">
                          <div className="flex flex-row">
                            <span className="inline-flex flex-shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20 dark:bg-green-400/10 dark:text-green-400">
                              {chosenConnection.id}
                            </span>
                          </div>
                        </div>
                      )}
                    </div>
                  </DisclosureButton>
                  <DisclosurePanel>
                    <SearchInput
                      value={searchTerm}
                      onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                        setSearchTerm(e.target.value)
                      }
                      className="mb-4"
                    />
                    <ul
                      role="list"
                      className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
                    >
                      {filteredConnections.map((connection) => {
                        // TODO: Here we will enable the dump for PostgresSQL when pg_dump issue is resolved in the backend
                        // Only enable DB dump for MySQL
                        const sqlDumpEnabled =
                          connection._type === "DATASOURCE" &&
                          connection.type === DatabaseType.MYSQL;

                        return (
                          <Card
                            header={connection.displayName}
                            subheader={connection.description}
                            label={connection.id}
                            key={connection.id}
                            clickQuery={() => {
                              setChosenConnection(connection);
                              setChosenMode("SingleExecution");
                              close();
                            }}
                            clickAccess={() => {
                              setChosenConnection(connection);
                              setChosenMode("TemporaryAccess");
                              close();
                            }}
                            clickSQLDump={() => {
                              setChosenConnection(connection);
                              setChosenMode("SQLDump");
                              close();
                            }}
                            connectionType={connection._type}
                            sqlDumpEnabled={sqlDumpEnabled}
                          ></Card>
                        );
                      })}
                    </ul>
                  </DisclosurePanel>
                </>
              )}
            </Disclosure>
            {(chosenConnection &&
              chosenMode &&
              chosenConnection._type == "DATASOURCE" && (
                <DatasourceExecutionRequestForm
                  connection={chosenConnection}
                  mode={chosenMode}
                ></DatasourceExecutionRequestForm>
              )) ||
              (chosenConnection &&
                (chosenMode === "SingleExecution" ||
                  chosenMode === "TemporaryAccess") &&
                chosenConnection._type == "KUBERNETES" && (
                  <KubernetesExecutionRequestForm
                    connection={chosenConnection}
                    mode={chosenMode}
                  ></KubernetesExecutionRequestForm>
                ))}
          </div>
        )}
      </div>
    </div>
  );
}

const DatasourceExecutionRequestForm = ({
  connection,
  mode,
}: {
  connection: ConnectionResponse;
  mode: "SingleExecution" | "TemporaryAccess" | "SQLDump";
}) => {
  const navigate = useNavigate();

  const location = useLocation();

  const { addNotification } = useNotification();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
  } = useForm<DatasourceExecutionRequest>({
    resolver: zodResolver(DatasourceExecutionRequestSchema),
  });

  useEffect(() => {
    const state = location.state as PreConfiguredStateDatasource;
    setValue("connectionType", "DATASOURCE");
    setValue("connectionId", connection.id);
    setValue("type", mode);
    if (state) {
      setValue("title", state.title);
      setValue("description", state.description);
      setValue("statement", state.statement);
    }
  }, [connection, mode]);

  const onSubmit: SubmitHandler<DatasourceExecutionRequest> = async (
    data: DatasourceExecutionRequest,
  ) => {
    const response = await addRequest(data);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to create request",
        text: response.message,
        type: "error",
      });
    } else {
      navigate("/requests");
    }
  };

  return (
    <div className="mx-auto w-full">
      <form
        className="mx-auto w-full"
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
      >
        <span
          className={`mt-6 inline-block w-min rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-600 ring-1 ring-inset ring-yellow-500/10 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20`}
        >
          {mode}
        </span>
        <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
          <label
            htmlFor="connection-id"
            className="block text-xs font-medium text-slate-900 dark:text-slate-50"
          >
            Connection
          </label>
          <input
            type="text"
            readOnly
            id="connection-id"
            className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
            value={connection.id}
            {...register("connectionId")}
          />
        </div>
        <input type="hidden" id="type" {...register("type")}></input>
        <div className="my-3 rounded-md  px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
          <label
            htmlFor="title-input"
            className="block text-xs font-medium text-slate-900 dark:text-slate-50"
          >
            Title
          </label>
          <input
            className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
            id="title-input"
            type="text"
            placeholder={
              mode === "SQLDump" ? "Give the request a title" : "My query"
            }
            {...register("title")}
          />
          {errors.title && (
            <p className="mt-2 text-xs italic text-red-500">
              {errors.title?.message}
            </p>
          )}
        </div>
        <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
          <label
            htmlFor="description-input"
            className="block text-xs font-medium text-slate-900 dark:text-slate-50"
          >
            Description
          </label>
          <textarea
            className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
            id="description-input"
            placeholder={
              mode === "TemporaryAccess"
                ? "Why do you need access to this connection?"
                : mode === "SingleExecution"
                ? "What are you trying to accomplish with this Query?"
                : "Why do you need a SQL dump from this database?"
            }
            {...register("description")}
          ></textarea>
          {errors.description && (
            <p className="mt-2 text-xs italic text-red-500">
              {errors.description?.message}
            </p>
          )}
        </div>
        {mode === "SingleExecution" && (
          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="statement-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              {languageString(connection)}
            </label>
            <textarea
              className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
              id="statement-input"
              placeholder={queryPlaceholder(connection)}
              {...register("statement")}
            ></textarea>
            {errors.statement && (
              <p className="mt-2 text-xs italic text-red-500">
                {errors.statement?.message}
              </p>
            )}
          </div>
        )}
        <div className="-mx-3 mb-2 flex flex-wrap">
          <div className="mb-6 ml-auto px-3">
            <Button type="submit">Submit</Button>
          </div>
        </div>
      </form>
    </div>
  );
};

const usePods = () => {
  const [pods, setPods] = useState<Pod[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { addNotification } = useNotification();

  useEffect(() => {
    const fetchData = async () => {
      const response = await getPods();
      if (isApiErrorResponse(response)) {
        addNotification({
          title: "Failed to load pods",
          text: response.message,
          type: "error",
        });
      } else {
        setPods(response.pods);
      }
      setLoading(false);
    };
    void fetchData();
  }, []);

  return { pods, loading };
};

const KubernetesExecutionRequestForm = ({
  connection,
  mode,
}: {
  connection: ConnectionResponse;
  mode: "SingleExecution" | "TemporaryAccess";
}) => {
  const navigate = useNavigate();

  const location = useLocation();

  const { addNotification } = useNotification();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
  } = useForm<KubernetesExecutionRequest>({
    resolver: zodResolver(KubernetesExecutionRequestSchema),
  });

  const { pods, loading } = usePods();

  const [chosenPod, setChosenPod] = useState<Pod | undefined>(undefined);

  useEffect(() => {
    const state = location.state as PreConfiguredStateKubernetes;
    setValue("connectionType", "KUBERNETES");
    setValue("connectionId", connection.id);
    setValue("type", mode);
    if (state) {
      setValue("title", state.title);
      setValue("description", state.description);
      setValue("command", state.command);
      setValue("namespace", state.namespace);
      setValue("podName", state.podName);
      setValue("containerName", state.containerName);
    }
  }, [connection, mode]);

  const choosePod = (id: string) => {
    const selectedPod = pods.find((p) => p.id === id);
    if (selectedPod) {
      setChosenPod(selectedPod);
      setValue("podName", selectedPod.name);
      setValue("namespace", selectedPod.namespace);
    }
  };

  const onSubmit: SubmitHandler<KubernetesExecutionRequest> = async (
    data: KubernetesExecutionRequest,
  ) => {
    const response = await addRequest(data);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to create request",
        text: response.message,
        type: "error",
      });
    } else {
      navigate("/requests");
    }
  };

  return (
    <div className="mx-auto w-full">
      {(loading && <Spinner></Spinner>) || (
        <form
          className="mx-auto w-full"
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <span
            className={`mt-6 inline-block w-min rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-600 ring-1 ring-inset ring-yellow-500/10 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20`}
          >
            {mode}
          </span>
          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="connection-id"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Connection
            </label>
            <input
              type="text"
              readOnly
              id="connection-id"
              className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
              value={connection.id}
              {...register("connectionId")}
            />
          </div>
          <div className="my-3 rounded-md  px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="title-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Title
            </label>
            <input
              className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
              id="title-input"
              type="text"
              placeholder="My query"
              {...register("title")}
            />
            {errors.title && (
              <p className="mt-2 text-xs italic text-red-500">
                {errors.title?.message}
              </p>
            )}
          </div>
          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="description-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Description
            </label>
            <textarea
              className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
              id="description-input"
              placeholder={
                mode === "TemporaryAccess"
                  ? "Why do you need access to this connection?"
                  : "What are you trying to accomplish?"
              }
              {...register("description")}
            ></textarea>
            {errors.description && (
              <p className="mt-2 text-xs italic text-red-500">
                {errors.description?.message}
              </p>
            )}
          </div>
          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="podName"
              className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
            >
              Pod
            </label>
            <select
              className="mt-2 block w-full rounded-md border-0 pr-10 text-slate-900 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-300 sm:text-sm sm:leading-6"
              onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                choosePod(e.target.value);
              }}
            >
              {pods.map((pod) => (
                <option value={pod.id}>{pod.name}</option>
              ))}
            </select>
          </div>
          <input type="hidden" id="type" {...register("type")}></input>

          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="namespace-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Namespace
            </label>
            <input
              className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
              id="namespace-input"
              type="text"
              readOnly
              placeholder="default"
              {...register("namespace")}
            />
            {errors.namespace && (
              <p className="mt-2 text-xs italic text-red-500">
                {errors.namespace?.message}
              </p>
            )}
          </div>
          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="pod-name-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Pod Name
            </label>
            <input
              className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
              id="pod-name-input"
              type="text"
              readOnly
              placeholder="my-pod"
              {...register("podName")}
            />
            {errors.podName && (
              <p className="mt-2 text-xs italic text-red-500">
                {errors.podName?.message}
              </p>
            )}
          </div>
          <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
            <label
              htmlFor="container-name-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Container Name
            </label>
            <select
              className="mt-2 block w-full rounded-md border-0 pr-10 text-slate-900 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-300 sm:text-sm sm:leading-6"
              {...register("containerName")}
            >
              {chosenPod?.containerNames.map((containerName) => (
                <option value={containerName}>{containerName}</option>
              ))}
            </select>
            {errors.containerName && (
              <p className="mt-2 text-xs italic text-red-500">
                {errors.containerName?.message}
              </p>
            )}
          </div>
          {mode === "SingleExecution" && (
            <div className="my-3 rounded-md px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 focus-within:ring-2 focus-within:ring-indigo-600 dark:ring-slate-700">
              <label
                htmlFor="command-input"
                className="block text-xs font-medium text-slate-900 dark:text-slate-50"
              >
                Command
              </label>
              <div className="flex">
                <span className="mr-3 whitespace-nowrap text-slate-400 dark:text-slate-400">
                  /bin/sh -c
                </span>
                <textarea
                  className="block w-full bg-slate-50 p-0 text-slate-900 ring-0 placeholder:text-slate-400 focus:ring-0 focus-visible:outline-none dark:bg-slate-950 dark:text-slate-50 sm:text-sm sm:leading-6"
                  id="command-input"
                  placeholder="echo hello world"
                  {...register("command")}
                ></textarea>
              </div>
              {errors.command && (
                <p className="mt-2 text-xs italic text-red-500">
                  {errors.command?.message}
                </p>
              )}
            </div>
          )}
          <div className="-mx-3 mb-2 flex flex-wrap">
            <div className="mb-6 ml-auto px-3">
              <Button type="submit">Submit</Button>
            </div>
          </div>
        </form>
      )}
    </div>
  );
};

interface CardProps {
  header: string;
  label: string;
  subheader: string;
  clickQuery: () => void;
  clickAccess: () => void;
  clickSQLDump: () => void;
  connectionType: "DATASOURCE" | "KUBERNETES";
  sqlDumpEnabled: boolean;
}

const Card = (props: CardProps) => {
  return (
    <li className="col-span-1 flex flex-col justify-between divide-y divide-slate-200 rounded-lg border bg-white shadow dark:divide-slate-700 dark:border-slate-700 dark:bg-slate-900">
      <div className="flex w-full items-center justify-between space-x-6 p-6">
        <div className="w-full flex-1">
          <div className="flex w-full items-center justify-between space-x-2">
            <span className="line-clamp-1 max-w-[50%] whitespace-nowrap text-sm font-medium text-slate-900 dark:text-slate-50">
              {props.header}
            </span>
            <span
              className=" line-clamp-1 max-w-[50%] whitespace-nowrap rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20 dark:bg-green-400/10 dark:text-green-400"
              title={props.label}
            >
              {props.label}
            </span>
          </div>
          <p className="mt-1 line-clamp-3 break-normal text-sm text-slate-500 dark:text-slate-400">
            {props.subheader}
          </p>
        </div>
      </div>
      <div className="">
        <div className="-mt-px flex divide-x divide-slate-200 dark:divide-slate-700">
          <div className="flex w-0 flex-1">
            <button
              onClick={props.clickQuery}
              className="relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-bl-lg border border-transparent py-4 text-sm font-semibold text-slate-900 hover:bg-slate-100 dark:text-slate-50 dark:hover:bg-slate-800"
            >
              {props.connectionType === "DATASOURCE" ? (
                <CircleStackIcon
                  className="h-5 w-5 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
              ) : (
                <CloudIcon
                  className="h-5 w-5 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
              )}
              {props.connectionType === "DATASOURCE" ? "Query" : "Command"}
            </button>
          </div>
          {props.connectionType === "DATASOURCE" && (
            <div className="-ml-px flex w-0 flex-1">
              <button
                onClick={props.clickAccess}
                className="relative inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-br-lg border border-transparent py-4 text-sm font-semibold text-slate-900 hover:bg-slate-100 dark:text-slate-50 dark:hover:bg-slate-800"
              >
                <CommandLineIcon
                  className="h-5 w-5 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
                Access
              </button>
            </div>
          )}
          {props.connectionType === "DATASOURCE" && props.sqlDumpEnabled && (
            <div className="-ml-px flex w-0 flex-1">
              <button
                onClick={props.clickSQLDump}
                className="relative inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-br-lg border border-transparent py-4 text-sm font-semibold text-slate-900 hover:bg-slate-100 dark:text-slate-50 dark:hover:bg-slate-800"
              >
                <CircleStackIcon
                  className="h-5 w-5 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
                DB Dump
              </button>
            </div>
          )}
        </div>
      </div>
    </li>
  );
};

export type { ExecutionRequest };
