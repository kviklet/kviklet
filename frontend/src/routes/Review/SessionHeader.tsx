import { Link } from "react-router-dom";
import { CircleStackIcon, ClockIcon } from "@heroicons/react/20/solid";
import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import Breadcrumbs from "../../components/Breadcrumbs";
import { sessionAccess } from "./sessionAccess";

export default function SessionHeader({
  request,
  active,
  access,
}: {
  request: ExecutionRequestResponseWithComments;
  active: boolean;
  access: ReturnType<typeof sessionAccess>;
}) {
  const base = `/requests/${encodeURIComponent(request.id)}`;
  return (
    <header className="mb-6">
      <Breadcrumbs
        items={[
          { label: "Requests", to: "/requests" },
          { label: request.title },
        ]}
      />
      <h1 className="mb-3 mt-2 text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
        {request.title}
      </h1>
      <div className="mb-5 flex flex-wrap items-center gap-x-6 gap-y-2 text-sm text-slate-600 dark:text-slate-400">
        <span className="flex items-center gap-2">
          <CircleStackIcon className="h-4 w-4" />
          {request.connection.displayName}
        </span>
        <span
          className={`flex flex-wrap items-center gap-2 ${
            access.expired ? "text-amber-700 dark:text-amber-400" : ""
          }`}
          data-testid="session-access-status"
        >
          <ClockIcon className="h-4 w-4" />
          {access.label}
          {access.expiresAt !== undefined && !access.expired && (
            <span>
              · Expires at{" "}
              {new Date(access.expiresAt).toLocaleTimeString([], {
                hour: "2-digit",
                minute: "2-digit",
              })}
            </span>
          )}
        </span>
      </div>
      <nav
        aria-label="Request views"
        className="flex gap-6 border-b border-slate-200 dark:border-slate-700"
      >
        {[
          { label: "Overview", to: base, selected: !active },
          { label: "Session", to: `${base}/session`, selected: active },
        ].map((tab) => (
          <Link
            key={tab.label}
            to={tab.to}
            aria-current={tab.selected ? "page" : undefined}
            className={`border-b-2 px-1 pb-3 text-sm font-medium transition-colors ${
              tab.selected
                ? "border-indigo-600 text-indigo-700 dark:border-indigo-400 dark:text-indigo-400"
                : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800 dark:text-slate-400 dark:hover:border-slate-600 dark:hover:text-slate-200"
            }`}
          >
            {tab.label}
          </Link>
        ))}
      </nav>
    </header>
  );
}
