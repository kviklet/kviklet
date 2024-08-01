export default function Auditlog() {
  return (
    <div>
      <div className=" border-b border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-950">
        <h1 className=" m-5 mx-auto max-w-5xl pl-1.5 text-xl">Auditlog</h1>
      </div>
      <div className="mx-auto max-w-5xl">
        <List></List>
      </div>
    </div>
  );
}

import { ChevronRightIcon } from "@heroicons/react/20/solid";
import InitialBubble from "../components/InitialBubble";
import { Link } from "react-router-dom";
import { ExecutionLogResponse, getExecutions } from "../api/ExecutionsApi";
import { useEffect, useState } from "react";
import { timeSince } from "./Requests";
import { isApiErrorResponse } from "../api/Errors";
import SearchInput from "../components/SearchInput";

function useExecutions() {
  const [executions, setExecutions] = useState<ExecutionLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      const response = await getExecutions();
      if (isApiErrorResponse(response)) {
        console.error(response);
      } else {
        setExecutions(response.executions);
      }
      setLoading(false);
    };
    void fetchData();
  }, []);
  return { executions, loading };
}

function List() {
  const { executions, loading } = useExecutions();
  const [searchTerm, setSearchTerm] = useState("");
  const filteredExecutions = executions.filter(
    (execution) =>
      execution.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      execution.statement.toLowerCase().includes(searchTerm.toLowerCase()) ||
      execution.connectionId.toLowerCase().includes(searchTerm.toLowerCase()),
  );
  return (
    <div>
      <SearchInput
        value={searchTerm}
        onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
          setSearchTerm(e.target.value);
        }}
        placeholder="Search Auditlog"
        className="my-4 w-full"
      />
      <div className="overflow-hidden bg-white shadow dark:bg-slate-900 sm:rounded-md">
        <ul
          role="list"
          className="divide-y divide-slate-100 dark:divide-slate-800"
        >
          {loading
            ? Array.from({ length: 5 }).map(() => <ItemSkeleton />)
            : filteredExecutions.map((execution) => (
                <Item execution={execution}></Item>
              ))}
        </ul>
      </div>
    </div>
  );
}

function ItemSkeleton() {
  return (
    <li className="relative flex animate-pulse justify-between gap-x-6 py-5">
      <div className="flex min-w-0 gap-x-4">
        <div className="h-6 w-6 rounded-full bg-slate-200 dark:bg-slate-800"></div>
        <div className="min-w-0 flex-auto space-y-2">
          <div className="h-4 w-3/4 rounded bg-slate-200 dark:bg-slate-800"></div>
          <div className="h-4 rounded bg-slate-200 dark:bg-slate-800"></div>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-x-4">
        <div className="hidden space-y-2 sm:flex sm:flex-col sm:items-end">
          <div className="inline-flex h-4 flex-shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20 dark:bg-green-400/10 dark:text-green-400"></div>
          <div className="h-4 w-16 rounded bg-slate-200 dark:bg-slate-800"></div>
        </div>
        <div className="h-5 w-5 rounded-full bg-slate-200 dark:bg-slate-800"></div>
      </div>
    </li>
  );
}

function Item({ execution }: { execution: ExecutionLogResponse }) {
  return (
    <Link to={`/requests/${execution.requestId}`}>
      <li
        key={execution.statement}
        className="relative flex justify-between gap-x-6 border-b px-2 py-5 hover:bg-slate-50 dark:border-slate-700 dark:hover:bg-slate-800"
      >
        <div className="flex min-w-0 items-center gap-x-4">
          <InitialBubble name={execution.name} className="shrink-0" />
          <div className="min-w-0 flex-auto">
            <p className="text-sm font-semibold leading-6 text-slate-900 dark:text-slate-50">
              <span className="absolute inset-x-0 -top-px bottom-0" />
              {execution.name}
            </p>
            <p className="relative mt-1 flex truncate text-xs leading-5 text-slate-500 dark:text-slate-400">
              {execution.statement}
            </p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-x-4">
          <div className="hidden sm:flex sm:flex-col sm:items-end">
            <span className="inline-flex flex-shrink-0 items-center rounded-full bg-green-50 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20 dark:bg-green-400/10 dark:text-green-400">
              {execution.connectionId}
            </span>
            <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">
              Executed{" "}
              <time dateTime={execution.executionTime.toISOString()}>
                {timeSince(execution.executionTime)}
              </time>
            </p>
          </div>
          <ChevronRightIcon
            className="h-5 w-5 flex-none text-slate-400 dark:text-slate-500"
            aria-hidden="true"
          />
        </div>
      </li>
    </Link>
  );
}
