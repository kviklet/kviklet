export default function Auditlog() {
  return (
    <div>
      <div className="border-b border-slate-300 mb-3 dark:border-slate-700">
        <h1 className="text-xl pl-1.5 w-3/4 m-5 mx-auto">Auditlog</h1>
      </div>
      <div className="max-w-7xl mx-auto">
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

function useExecutions() {
  const [executions, setExecutions] = useState<ExecutionLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      const executions = await getExecutions();
      setExecutions(executions.executions);
      setLoading(false);
    };
    void fetchData();
  }, []);
  return { executions, loading };
}

function List() {
  const { executions, loading } = useExecutions();
  return (
    <div className="bg-white dark:bg-slate-900 shadow overflow-hidden sm:rounded-md">
      <ul
        role="list"
        className="divide-y divide-slate-100 dark:divide-slate-800"
      >
        {loading
          ? Array.from({ length: 5 }).map(() => <ItemSkeleton />)
          : executions.map((execution) => <Item execution={execution}></Item>)}
      </ul>
    </div>
  );
}

function ItemSkeleton() {
  return (
    <li className="relative flex justify-between gap-x-6 py-5 animate-pulse">
      <div className="flex min-w-0 gap-x-4">
        <div className="w-6 h-6 bg-slate-200 dark:bg-slate-800 rounded-full"></div>
        <div className="min-w-0 flex-auto space-y-2">
          <div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-3/4"></div>
          <div className="h-4 bg-slate-200 dark:bg-slate-800 rounded"></div>
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-x-4">
        <div className="hidden sm:flex sm:flex-col sm:items-end space-y-2">
          <div className="inline-flex flex-shrink-0 items-center rounded-full bg-green-50 dark:bg-green-400/10 dark:text-green-400 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20 h-4"></div>
          <div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-16"></div>
        </div>
        <div className="h-5 w-5 bg-slate-200 dark:bg-slate-800 rounded-full"></div>
      </div>
    </li>
  );
}

function Item({ execution }: { execution: ExecutionLogResponse }) {
  return (
    <Link to={`/requests/${execution.requestId}`}>
      <li
        key={execution.statement}
        className="relative flex justify-between gap-x-6 py-5"
      >
        <div className="flex min-w-0 gap-x-4">
          <InitialBubble name={execution.name} className="shrink-0" />
          <div className="min-w-0 flex-auto">
            <p className="text-sm font-semibold leading-6 text-slate-900 dark:text-slate-50">
              <span className="absolute inset-x-0 -top-px bottom-0" />
              {execution.name}
            </p>
            <p className="mt-1 flex text-xs leading-5 text-slate-500 dark:text-slate-400 relative truncate">
              {execution.statement}
            </p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-x-4">
          <div className="hidden sm:flex sm:flex-col sm:items-end">
            <span className="inline-flex flex-shrink-0 items-center rounded-full bg-green-50 dark:bg-green-400/10 dark:text-green-400 px-1.5 py-0.5 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20">
              {execution.connectionId}
            </span>
            <p className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">
              Executed{" "}
              <time dateTime={execution.executionTime.toISOString()}>
                {timeAgo(execution.executionTime)}
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

export function timeAgo(input: string | Date): string {
  const now = new Date();
  const past = input instanceof Date ? input : new Date(input);
  const diffInSeconds = Math.floor((now.getTime() - past.getTime()) / 1000);

  let result = "";

  if (diffInSeconds < 60) {
    result = `${diffInSeconds}s ago`;
  } else if (diffInSeconds < 3600) {
    result = `${Math.floor(diffInSeconds / 60)}m ago`;
  } else if (diffInSeconds < 86400) {
    result = `${Math.floor(diffInSeconds / 3600)}h ago`;
  } else if (diffInSeconds < 2592000) {
    result = `${Math.floor(diffInSeconds / 86400)}d ago`;
  } else if (diffInSeconds < 31536000) {
    result = `${Math.floor(diffInSeconds / 2592000)}mo ago`;
  } else {
    result = `${Math.floor(diffInSeconds / 31536000)}y ago`;
  }

  return result;
}
