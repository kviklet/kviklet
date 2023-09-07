import { SubmitHandler, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { ConnectionResponse, fetchDatabases } from "../api/DatasourceApi";
import { useEffect, useState } from "react";
import { addRequest } from "../api/ExecutionRequestApi";
import { redirect, useNavigate } from "react-router-dom";
import Button from "../components/Button";

const ExecutionRequestSchema = z.object({
  title: z.string().min(1, { message: "Title is required" }),
  description: z.string(),
  statement: z.string().min(1, { message: "An sql statement is required" }),
  connection: z.string().min(1),
});

type ExecutionRequest = z.infer<typeof ExecutionRequestSchema>;

function AddRequestForm() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const navigate = useNavigate();

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
    navigate("/requests");
  };

  return (
    <div className="mt-10">
      <form
        className="w-full max-w-lg mx-auto"
        onSubmit={handleSubmit(onSubmit)}
      >
        <div className="flex flex-wrap -mx-3 mb-6">
          <div className="w-full px-3 mb-6">
            <label
              className="block uppercase tracking-wide text-slate-700 dark:text-slate-200 text-xs font-bold mb-2"
              htmlFor="connection-input"
            >
              Connection
            </label>
            <div className="relative">
              <select
                className="w-full px-3 py-2 rounded-md border 
              border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
                focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
              dark:focus:border-gray-500 sm:text-sm transition-colors"
                id="connection-input"
                {...register("connection")}
              >
                {connections.map((connection) => (
                  <option
                    className="bg-slate-200 dark:bg-slate-800 text-slate-900 dark:text-slate-100"
                    value={connection.id}
                  >
                    {connection.displayName}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="w-full px-3">
            <label
              className="block uppercase tracking-wide text-slate-700 dark:text-slate-200 text-xs font-bold mb-2"
              htmlFor="title-input"
            >
              Title
            </label>
            <input
              className="w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 sm:text-sm transition-colors"
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
        </div>
        <div className="flex flex-wrap -mx-3 mb-6">
          <div className="w-full px-3">
            <label
              className="block uppercase tracking-wide text-slate-700 dark:text-slate-200 text-xs font-bold mb-2"
              htmlFor="description-input"
            >
              Description
            </label>
            <textarea
              className="w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 sm:text-sm transition-colors"
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
              className="block uppercase tracking-wide text-slate-700 dark:text-slate-200 text-xs font-bold mb-2"
              htmlFor="statement-input"
            >
              Query / Statement
            </label>
            <textarea
              className="w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 sm:text-sm transition-colors"
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
          <div className="px-3 mb-6 ml-auto">
            <Button type="submit">Submit</Button>
          </div>
        </div>
      </form>
    </div>
  );
}

export { AddRequestForm };
export type { ExecutionRequest };
