import { SubmitHandler, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ConnectionResponse, fetchDatabases } from "../api/DatasourceApi";
import { useEffect, useState } from "react";
import { addRequest } from "../api/ExecutionRequestApi";

const ExecutionRequestSchema = z.object({
  issueLink: z.string().url(),
  title: z.string().min(1, { message: "Title is required" }),
  description: z.string(),
  statement: z.string().min(1, { message: "An sql statement is required" }),
  readOnly: z.boolean(),
  connection: z.string().min(1),
  confidential: z.boolean(),
});

type ExecutionRequest = z.infer<typeof ExecutionRequestSchema>;

function AddRequestForm() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);

  const datasource = useEffect(() => {
    const fetchData = async () => {
      const databases = await fetchDatabases();
      const requestedConnections = databases.flatMap((database) => {
        return database.datasourceConnections;
      });
      setConnections(requestedConnections);
    };
    fetchData();
  }, []);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ExecutionRequest>({
    resolver: zodResolver(ExecutionRequestSchema),
  });
  console.log(errors);

  const onSubmit: SubmitHandler<ExecutionRequest> = async (
    data: ExecutionRequest
  ) => {
    console.log(data);
    await addRequest(data);
  };

  return (
    <div>
      <form
        className="w-full max-w-lg mx-auto"
        onSubmit={handleSubmit(onSubmit)}
      >
        <div className="flex flex-wrap -mx-3 mb-6">
          <div className="w-full px-3 mb-6">
            <label
              className="block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="connection-input"
            >
              Connection
            </label>
            <div className="relative">
              <select
                className="block appearance-none w-full bg-gray-200 border border-gray-200 text-gray-700 py-3 px-4 pr-8 rounded leading-tight focus:outline-none focus:bg-white focus:border-gray-500"
                id="connection-input"
                {...register("connection")}
              >
                {connections.map((connection) => (
                  <option value={connection.id}>
                    {connection.displayName}
                  </option>
                ))}
              </select>
              <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center px-2 text-gray-700">
                <svg
                  className="fill-current h-4 w-4"
                  xmlns="http://www.w3.org/2000/svg"
                  viewBox="0 0 20 20"
                >
                  <path d="M10 12a2 2 0 1 1 0-4 2 2 0 0 1 0 4zm0 2a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" />
                </svg>
              </div>
            </div>
          </div>
          <div className="w-full px-3 mb-0">
            <label
              className="block uppercase t)racking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="ticket-input"
            >
              Link to Issue/Ticket
            </label>
            <input
              className="appearance-none block w-full bg-gray-200 text-gray-700 border rounded py-3 px-4 mb-3 leading-tight focus:outline-none focus:bg-white"
              id="ticket-input"
              type="text"
              placeholder="https://my-company.jira.com/TEAM-123"
              {...register("issueLink")}
            />
            {errors.issueLink && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.issueLink?.message}
              </p>
            )}
          </div>
          <div className="w-full px-3">
            <label
              className="block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="title-input"
            >
              Title
            </label>
            <input
              className="appearance-none block w-full bg-gray-200 text-gray-700 border border-gray-200 rounded py-3 px-4 leading-tight focus:outline-none focus:bg-white focus:border-gray-500"
              id="title-input"
              type="text"
              placeholder="My cute query"
              {...register("title")}
            />
            {errors.title && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.title?.message}
              </p>
            )}
          </div>
        </div>
        <div className="flex flex-wrap -mx-3 mb-6">
          <div className="w-full px-3">
            <label
              className="block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="description-input"
            >
              Description
            </label>
            <textarea
              className="appearance-none block w-full bg-gray-200 text-gray-700 border border-gray-200 rounded py-3 px-4 mb-3 leading-tight focus:outline-none focus:bg-white focus:border-gray-500"
              id="description-input"
              placeholder="Description"
              {...register("description")}
            ></textarea>
            {errors.description && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.description?.message}
              </p>
            )}
          </div>
        </div>
        <div className="flex flex-wrap -mx-3 mb-6">
          <div className="w-full px-3">
            <label
              className="block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="statement-input"
            >
              Query / Statement
            </label>
            <textarea
              className="appearance-none block w-full bg-gray-200 text-gray-700 border border-gray-200 rounded py-3 px-4 mb-3 leading-tight focus:outline-none focus:bg-white focus:border-gray-500"
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
        </div>
        <div className="flex flex-wrap -mx-3 mb-2">
          <div className="w-full px-3 mb-6">
            <label
              className="block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="readonly-input"
            >
              Read Only
            </label>
            <input
              className=""
              id="readonly-input"
              type="checkbox"
              {...register("readOnly")}
            />
            {errors.readOnly && (
              <p className="text-xs italic text-red-500 mt-2">
                {errors.readOnly?.message}
              </p>
            )}
          </div>
          <div className="w-full px-3 mb-6">
            <label
              className="block uppercase tracking-wide text-gray-700 text-xs font-bold mb-2"
              htmlFor="confidential-input"
            >
              Confidential
            </label>
            <input
              className=""
              id="confidential-input"
              type="checkbox"
              {...register("confidential")}
            />
          </div>
        </div>
        <div className="flex flex-wrap -mx-3 mb-2">
          <div className="w-full px-3 mb-6">
            <button
              className="bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"
              type="submit"
            >
              Submit
            </button>
          </div>
        </div>
      </form>
    </div>
  );
}

export { AddRequestForm };
export type { ExecutionRequest };
