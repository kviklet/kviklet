import { useEffect, useState } from "react";
import { ConnectionResponse, getConnections } from "../api/DatasourceApi";
import Spinner from "../components/Spinner";
import {
  ChevronDownIcon,
  ChevronRightIcon,
  CircleStackIcon,
  CloudIcon,
  CommandLineIcon,
} from "@heroicons/react/20/solid";
import { useNavigate } from "react-router-dom";
import { SubmitHandler, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { addRequest } from "../api/ExecutionRequestApi";
import { z } from "zod";
import Button from "../components/Button";
import { Disclosure } from "@headlessui/react";
import { Pod, getPods } from "../api/KubernetesApi";
import useConnections from "../hooks/connections";

const DatasourceExecutionRequestSchema = z
  .object({
    connectionType: z.literal("DATASOURCE"),
    title: z.string().min(1, { message: "Title is required" }),
    type: z.enum(["TemporaryAccess", "SingleExecution"]),
    description: z.string(),
    statement: z.string().optional(),
    connectionId: z.string().min(1),
  })
  .refine(
    (data) =>
      data.type === "TemporaryAccess" ||
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

export default function ConnectionChooser() {
  const { connections, loading } = useConnections();
  const [chosenConnection, setChosenConnection] = useState<
    ConnectionResponse | undefined
  >(undefined);
  const [chosenMode, setChosenMode] = useState<
    "SingleExecution" | "TemporaryAccess" | undefined
  >(undefined);

  return (
    <div>
      <div className=" border-b border-slate-300 bg-slate-50 dark:bg-slate-950 dark:border-slate-700">
        <h1 className=" max-w-5xl mx-auto text-xl m-5 pl-1.5">
          {" "}
          Request Access to a Database
        </h1>
      </div>
      <div className="flex max-w-5xl mx-auto mt-5">
        {loading ? (
          <Spinner></Spinner>
        ) : (
          <div className="w-full">
            <Disclosure defaultOpen={true}>
              {({ open, close }) => (
                <>
                  <Disclosure.Button className="py-2">
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
                            <span className="inline-flex flex-shrink-0 items-center rounded-full bg-green-50 dark:bg-green-400/10 dark:text-green-400 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
                              {chosenConnection.id}
                            </span>
                          </div>
                        </div>
                      )}
                    </div>
                  </Disclosure.Button>
                  <Disclosure.Panel>
                    <ul
                      role="list"
                      className="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3"
                    >
                      {connections.map((connection) => (
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
                          connectionType={connection._type}
                        ></Card>
                      ))}
                    </ul>
                  </Disclosure.Panel>
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
                chosenMode &&
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
  mode: "SingleExecution" | "TemporaryAccess";
}) => {
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
  } = useForm<DatasourceExecutionRequest>({
    resolver: zodResolver(DatasourceExecutionRequestSchema),
  });

  useEffect(() => {
    setValue("connectionType", "DATASOURCE");
    setValue("connectionId", connection.id);
    setValue("type", mode);
  }, [connection, mode]);

  const onSubmit: SubmitHandler<DatasourceExecutionRequest> = async (
    data: DatasourceExecutionRequest,
  ) => {
    await addRequest(data);
    navigate("/requests");
  };

  return (
    <div className="mx-auto w-full">
      <form
        className="w-full mx-auto"
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
      >
        <span
          className={`inline-block w-min rounded-md px-2 py-1 mt-6 text-xs font-medium ring-1 ring-inset bg-yellow-50 text-yellow-600 ring-yellow-500/10 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20`}
        >
          {mode}
        </span>
        <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
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
            className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
            value={connection.id}
            {...register("connectionId")}
          />
        </div>
        <input type="hidden" id="type" {...register("type")}></input>
        <div className="rounded-md my-3  px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
          <label
            htmlFor="title-input"
            className="block text-xs font-medium text-slate-900 dark:text-slate-50"
          >
            Title
          </label>
          <input
            className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
            id="title-input"
            type="text"
            placeholder="My query"
            {...register("title")}
          />
          {errors.title && (
            <p className="text-xs italic text-red-500 mt-2">
              {errors.title?.message}
            </p>
          )}
        </div>
        <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
          <label
            htmlFor="description-input"
            className="block text-xs font-medium text-slate-900 dark:text-slate-50"
          >
            Description
          </label>
          <textarea
            className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
            id="description-input"
            placeholder={
              mode === "TemporaryAccess"
                ? "Why do you need access to this connection?"
                : "What are you trying to accomplish with this Query?"
            }
            {...register("description")}
          ></textarea>
          {errors.description && (
            <p className="text-xs italic text-red-500 mt-2">
              {errors.description?.message}
            </p>
          )}
        </div>
        {mode === "SingleExecution" && (
          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="description-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              SQL
            </label>
            <textarea
              className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
              id="statement-input"
              placeholder="Select id from some_table;"
              {...register("statement")}
            ></textarea>
            {errors.statement && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.statement?.message}
              </p>
            )}
          </div>
        )}
        <div className="flex flex-wrap -mx-3 mb-2">
          <div className="px-3 mb-6 ml-auto">
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

  useEffect(() => {
    const fetchData = async () => {
      const response = await getPods();
      setPods(response.pods);
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
    setValue("connectionType", "KUBERNETES");
    setValue("connectionId", connection.id);
    setValue("type", mode);
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
    await addRequest(data);
    navigate("/requests");
  };

  return (
    <div className="mx-auto w-full">
      {(loading && <Spinner></Spinner>) || (
        <form
          className="w-full mx-auto"
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        >
          <span
            className={`inline-block w-min rounded-md px-2 py-1 mt-6 text-xs font-medium ring-1 ring-inset bg-yellow-50 text-yellow-600 ring-yellow-500/10 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20`}
          >
            {mode}
          </span>
          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
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
              className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
              value={connection.id}
              {...register("connectionId")}
            />
          </div>
          <div className="rounded-md my-3  px-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="title-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Title
            </label>
            <input
              className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
              id="title-input"
              type="text"
              placeholder="My query"
              {...register("title")}
            />
            {errors.title && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.title?.message}
              </p>
            )}
          </div>
          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="description-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Description
            </label>
            <textarea
              className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
              id="description-input"
              placeholder={
                mode === "TemporaryAccess"
                  ? "Why do you need access to this connection?"
                  : "What are you trying to accomplish?"
              }
              {...register("description")}
            ></textarea>
            {errors.description && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.description?.message}
              </p>
            )}
          </div>
          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="podName"
              className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
            >
              Pod
            </label>
            <select
              className="mt-2 block w-full dark:bg-slate-950 rounded-md border-0 pr-10 text-slate-900 sm:text-sm sm:leading-6 dark:text-slate-300 focus-visible:outline-none focus:ring-0"
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

          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="namespace-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Namespace
            </label>
            <input
              className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
              id="namespace-input"
              type="text"
              readOnly
              placeholder="default"
              {...register("namespace")}
            />
            {errors.namespace && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.namespace?.message}
              </p>
            )}
          </div>
          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="pod-name-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Pod Name
            </label>
            <input
              className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
              id="pod-name-input"
              type="text"
              readOnly
              placeholder="my-pod"
              {...register("podName")}
            />
            {errors.podName && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.podName?.message}
              </p>
            )}
          </div>
          <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
            <label
              htmlFor="container-name-input"
              className="block text-xs font-medium text-slate-900 dark:text-slate-50"
            >
              Container Name
            </label>
            <select
              className="mt-2 block w-full dark:bg-slate-950 rounded-md border-0 pr-10 text-slate-900 sm:text-sm sm:leading-6 dark:text-slate-300 focus-visible:outline-none focus:ring-0"
              {...register("containerName")}
            >
              {chosenPod?.containerNames.map((containerName) => (
                <option value={containerName}>{containerName}</option>
              ))}
            </select>
            {errors.containerName && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.containerName?.message}
              </p>
            )}
          </div>
          {mode === "SingleExecution" && (
            <div className="rounded-md px-3 my-3 pb-1.5 pt-2.5 shadow-sm ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus-within:ring-2 focus-within:ring-indigo-600">
              <label
                htmlFor="command-input"
                className="block text-xs font-medium text-slate-900 dark:text-slate-50"
              >
                Command
              </label>
              <div className="flex">
                <span className="whitespace-nowrap text-slate-400 dark:text-slate-400 mr-3">
                  /bin/sh -c
                </span>
                <textarea
                  className="block w-full ring-0 p-0 text-slate-900 placeholder:text-slate-400 focus:ring-0 sm:text-sm sm:leading-6 focus-visible:outline-none bg-slate-50 dark:bg-slate-950 dark:text-slate-50"
                  id="command-input"
                  placeholder="echo hello world"
                  {...register("command")}
                ></textarea>
              </div>
              {errors.command && (
                <p className="text-xs italic text-red-500 mt-2">
                  {errors.command?.message}
                </p>
              )}
            </div>
          )}
          <div className="flex flex-wrap -mx-3 mb-2">
            <div className="px-3 mb-6 ml-auto">
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
  connectionType: "DATASOURCE" | "KUBERNETES";
}

const Card = (props: CardProps) => {
  return (
    <li className="col-span-1 divide-y divide-slate-200 dark:divide-slate-700 rounded-lg bg-white dark:bg-slate-900 border dark:border-slate-700 shadow flex flex-col justify-between">
      <div className="flex w-full items-center justify-between space-x-6 p-6">
        <div className="flex-1">
          <div className="flex items-center space-x-3">
            <h3 className="truncate text-sm font-medium text-slate-900 dark:text-slate-50">
              {props.header}
            </h3>
            <span className="inline-flex flex-shrink-0 items-center rounded-full bg-green-50 dark:bg-green-400/10 dark:text-green-400 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
              {props.label}
            </span>
          </div>
          <p className="mt-1 break-normal line-clamp-3 text-sm text-slate-500 dark:text-slate-400">
            {props.subheader}
          </p>
        </div>
      </div>
      <div className="">
        <div className="-mt-px flex divide-x divide-slate-200 dark:divide-slate-700">
          <div className="flex w-0 flex-1">
            <button
              onClick={props.clickQuery}
              className="relative -mr-px inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-bl-lg border border-transparent py-4 text-sm font-semibold text-slate-900 dark:text-slate-50 dark:hover:bg-slate-800 hover:bg-slate-100"
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
                className="relative inline-flex w-0 flex-1 items-center justify-center gap-x-3 rounded-br-lg border border-transparent py-4 text-sm font-semibold text-slate-900 dark:text-slate-50 dark:hover:bg-slate-800 hover:bg-slate-100"
              >
                <CommandLineIcon
                  className="h-5 w-5 text-slate-400 dark:text-slate-500"
                  aria-hidden="true"
                />
                Access
              </button>
            </div>
          )}
        </div>
      </div>
    </li>
  );
};

export type { ExecutionRequest };
