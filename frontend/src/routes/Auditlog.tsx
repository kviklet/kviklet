export default function Auditlog() {
  return (
    <div>
      <div className="border-b border-slate-300 bg-slate-50 dark:bg-slate-950 dark:border-slate-700">
        <h1 className="mx-auto max-w-7xl text-xl m-5 pl-1.5">Execution Log</h1>
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

function useExectuions() {
  const executions = [
    {
      requestId: "123",
      name: "Leslie Alexander",
      statement: "Select * from users",
      connectionId: "dev-root",
      executionTime: "2023-01-23T13:23Z",
    },
    {
      requestId: "123",
      name: "Michael Foster",
      statement: `SELECT
    e.employee_id,
    e.first_name,
    e.last_name,
    d.department_name,
    p.project_name,
    SUM(t.hours_worked) AS total_hours,
    AVG(t.hours_worked) AS average_hours,
    COUNT(DISTINCT p.project_id) AS number_of_projects
FROM
    employees e
JOIN
    departments d ON e.department_id = d.department_id
LEFT JOIN
    project_assignments pa ON e.employee_id = pa.employee_id
LEFT JOIN
    projects p ON pa.project_id = p.project_id
LEFT JOIN
    timesheets t ON e.employee_id = t.employee_id AND p.project_id = t.project_id
WHERE
    d.department_name = 'IT' AND
    e.hire_date > '2020-01-01'
GROUP BY
    e.employee_id,
    e.first_name,
    e.last_name,
    d.department_name,
    p.project_name
HAVING
    SUM(t.hours_worked) > 100
ORDER BY
    total_hours DESC,
    e.last_name;`,
      connectionId: "prod-read-only",
      executionTime: "2023-01-23T13:23Z",
    },
    {
      requestId: "123",
      name: "Dries Vincent",
      statement: "Select * from users",
      connectionId: "test-environment",
      executionTime: "2023-01-23T13:23Z",
    },
    {
      requestId: "123",
      name: "Lindsay Walton",
      statement: "Select * from users",
      connectionId: "staging-area",
      executionTime: "2023-01-23T13:23Z",
    },
    {
      requestId: "123",
      name: "Courtney Henry",
      statement: "Select * from users",
      connectionId: "user-acceptance-testing",
      executionTime: "2023-01-23T13:23Z",
    },
    {
      requestId: "123",
      name: "Tom Cook",
      statement: "Select * from users",
      connectionId: "production-support",
      executionTime: "2023-01-23T13:23Z",
    },
  ];
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

function Item({ execution }: { execution: (typeof executions)[0] }) {
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
              <a href={execution.href}>
                <span className="absolute inset-x-0 -top-px bottom-0" />
                {execution.name}
              </a>
            </p>
            <p className="mt-1 flex text-xs leading-5 text-slate-500 dark:text-slate-400">
              <a
                href={`mailto:${execution.statement}`}
                className="relative truncate"
              >
                {execution.statement}
              </a>
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
              <time dateTime={execution.executionTime}>
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
