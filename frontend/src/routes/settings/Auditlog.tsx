export default function Auditlog() {
  return (
    <div>
      <div className="max-w-7xl mx-auto">
        <List></List>
      </div>
    </div>
  );
}

import { ChevronRightIcon } from "@heroicons/react/20/solid";
import InitialBubble from "../../components/InitialBubble";
import { Link } from "react-router-dom";
import { ExecutionLogResponse, getExecutions } from "../../api/ExecutionsApi";
import { useEffect, useState } from "react";

function useExectuions() {
  const [executions, setExecutions] = useState<ExecutionLogResponse[]>([]);
  useEffect(() => {
    const fetchData = async () => {
      const executions = await getExecutions();
      setExecutions(executions.executions);
    };
    void fetchData();
  }, []);
  return { executions };
}

function List() {
  const { executions } = useExectuions();
  return (
    <ul role="list" className="divide-y divide-slate-100 dark:divide-slate-800">
      {executions.map((execution) => (
        <Item execution={execution}></Item>
      ))}
    </ul>
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

function timeAgo(input: string | Date): string {
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
