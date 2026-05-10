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

import {
  ChevronRightIcon,
  ArrowDownTrayIcon,
  LockClosedIcon,
  XMarkIcon,
  CalendarDaysIcon,
} from "@heroicons/react/20/solid";
import InitialBubble from "../components/InitialBubble";
import { Link } from "react-router-dom";
import {
  ExecutionLogResponse,
  getExecutions,
  exportExecutions,
} from "../api/ExecutionsApi";
import { ChangeEvent, useEffect, useRef, useState } from "react";
import { timeSince } from "./Requests";
import { isApiErrorResponse } from "../api/Errors";
import SearchInput from "../components/SearchInput";
import Button from "../components/Button";
import useConfig from "../components/ConfigProvider";
import useNotification from "../hooks/useNotification";
import Tooltip from "../components/Tooltip";

function ExportButton() {
  const { config } = useConfig();
  const { addNotification } = useNotification();
  const [isExporting, setIsExporting] = useState(false);

  const hasEnterpriseLicense =
    config?.licenseValid &&
    config?.validUntil &&
    config.validUntil > new Date();

  const handleExport = async () => {
    if (!hasEnterpriseLicense) {
      return;
    }

    setIsExporting(true);
    try {
      await exportExecutions();
      addNotification({
        title: "Export successful",
        text: "Audit log exported successfully",
        type: "info",
      });
    } catch (error) {
      console.error("Export failed:", error);
      addNotification({
        title: "Export failed",
        text:
          error instanceof Error ? error.message : "Failed to export audit log",
        type: "error",
      });
    } finally {
      setIsExporting(false);
    }
  };

  if (!hasEnterpriseLicense) {
    return (
      <Tooltip content="Enterprise feature - License required">
        <button
          type="button"
          disabled
          className="flex cursor-not-allowed items-center gap-1.5 rounded-md border border-slate-300 bg-slate-100 px-3 py-1 text-sm font-medium leading-5 text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-500"
        >
          <LockClosedIcon className="h-4 w-4 flex-shrink-0" />
          <span className="whitespace-nowrap">Export</span>
        </button>
      </Tooltip>
    );
  }

  return (
    <Button
      variant={isExporting ? "disabled" : "primary"}
      onClick={() => void handleExport()}
      className="flex items-center"
    >
      <ArrowDownTrayIcon className="mr-1.5 h-4 w-4 flex-shrink-0" />
      <span className="whitespace-nowrap">
        {isExporting ? "Exporting..." : "Export"}
      </span>
    </Button>
  );
}

const toLocalInputValue = (d: Date | null): string => {
  if (!d) return "";
  const pad = (n: number) => n.toString().padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(
    d.getHours(),
  )}:${pad(d.getMinutes())}`;
};

const formatRangeChip = (from: Date | null, to: Date | null): string => {
  const fmt = (d: Date) =>
    d.toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  if (from && to) return `${fmt(from)} – ${fmt(to)}`;
  if (from) return `From ${fmt(from)}`;
  if (to) return `Until ${fmt(to)}`;
  return "Date range";
};

function DateRangeFilter({
  from,
  to,
  onChange,
  disabled = false,
}: {
  from: Date | null;
  to: Date | null;
  onChange: (from: Date | null, to: Date | null) => void;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const hasFilter = from !== null || to !== null;

  useEffect(() => {
    if (!open) return;
    const handlePointer = (e: MouseEvent) => {
      if (
        wrapperRef.current &&
        !wrapperRef.current.contains(e.target as Node)
      ) {
        setOpen(false);
      }
    };
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", handlePointer);
    document.addEventListener("keydown", handleKey);
    return () => {
      document.removeEventListener("mousedown", handlePointer);
      document.removeEventListener("keydown", handleKey);
    };
  }, [open]);

  const inputClasses =
    "w-full rounded-md border border-slate-300 bg-white px-2.5 py-1.5 text-sm text-slate-900 [color-scheme:light] focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100 dark:[color-scheme:dark] dark:focus:border-indigo-500 dark:focus:ring-indigo-500";

  if (disabled) {
    return (
      <Tooltip content="Enterprise feature - License required">
        <button
          type="button"
          disabled
          className="flex cursor-not-allowed items-center gap-1.5 rounded-md border border-slate-300 bg-slate-100 px-3 py-1 text-sm font-medium leading-5 text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-500"
        >
          <LockClosedIcon className="h-4 w-4 flex-shrink-0" />
          <span>Date range</span>
        </button>
      </Tooltip>
    );
  }

  return (
    <div ref={wrapperRef} className="relative">
      <button
        type="button"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className={`flex max-w-full items-center gap-1.5 rounded-md border px-3 py-1 text-sm font-medium leading-5 transition-colors ${
          hasFilter
            ? "border-indigo-300 bg-indigo-50 text-indigo-700 hover:border-indigo-400 hover:bg-indigo-100 dark:border-indigo-500/40 dark:bg-indigo-500/10 dark:text-indigo-300 dark:hover:border-indigo-500/60 dark:hover:bg-indigo-500/15"
            : "border-slate-300 bg-white text-slate-700 hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800"
        }`}
      >
        <CalendarDaysIcon className="h-4 w-4 flex-shrink-0" />
        <span className="truncate">{formatRangeChip(from, to)}</span>
        {hasFilter && (
          <span
            role="button"
            tabIndex={0}
            aria-label="Clear date range"
            onClick={(e) => {
              e.stopPropagation();
              onChange(null, null);
            }}
            onKeyDown={(e) => {
              if (e.key === "Enter" || e.key === " ") {
                e.preventDefault();
                e.stopPropagation();
                onChange(null, null);
              }
            }}
            className="-mr-1 ml-0.5 inline-flex h-4 w-4 flex-shrink-0 items-center justify-center rounded-sm text-indigo-500 hover:bg-indigo-200/70 hover:text-indigo-800 dark:text-indigo-300/70 dark:hover:bg-indigo-500/30 dark:hover:text-indigo-100"
          >
            <XMarkIcon className="h-3.5 w-3.5" />
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Filter by date range"
          className="absolute right-0 z-20 mt-2 w-72 max-w-[calc(100vw-2rem)] origin-top-right rounded-lg border border-slate-200 bg-white p-3.5 shadow-lg ring-1 ring-black/5 dark:border-slate-700 dark:bg-slate-900 dark:ring-white/5"
        >
          <div className="space-y-3">
            <div>
              <label className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                From
              </label>
              <input
                type="datetime-local"
                value={toLocalInputValue(from)}
                onChange={(e) => {
                  const val = e.target.value;
                  onChange(val ? new Date(val) : null, to);
                }}
                className={inputClasses}
              />
            </div>
            <div>
              <label className="mb-1 block text-xs font-medium uppercase tracking-wide text-slate-500 dark:text-slate-400">
                To
              </label>
              <input
                type="datetime-local"
                value={toLocalInputValue(to)}
                onChange={(e) => {
                  const val = e.target.value;
                  onChange(from, val ? new Date(val) : null);
                }}
                className={inputClasses}
              />
            </div>
            <div className="flex items-center justify-between border-t border-slate-100 pt-3 dark:border-slate-800">
              <button
                type="button"
                disabled={!hasFilter}
                onClick={() => onChange(null, null)}
                className="inline-flex items-center gap-1 rounded-md px-1.5 py-1 text-xs text-slate-500 disabled:cursor-not-allowed disabled:opacity-40 hover:bg-slate-100 hover:text-slate-700 disabled:hover:bg-transparent disabled:hover:text-slate-500 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200"
              >
                <XMarkIcon className="h-3.5 w-3.5" />
                Clear
              </button>
              <button
                type="button"
                onClick={() => setOpen(false)}
                className="rounded-md bg-indigo-600 px-3 py-1 text-xs font-medium text-white hover:bg-indigo-700 dark:bg-indigo-500 dark:hover:bg-indigo-400"
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

function useExecutions(from: Date | null, to: Date | null) {
  const [executions, setExecutions] = useState<ExecutionLogResponse[]>([]);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      const response = await getExecutions({
        from: from ?? undefined,
        to: to ?? undefined,
      });
      if (isApiErrorResponse(response)) {
        console.error(response);
      } else {
        setExecutions(response.executions);
      }
      setLoading(false);
    };
    void fetchData();
  }, [from, to]);
  return { executions, loading };
}

function List() {
  const { config } = useConfig();
  const hasEnterpriseLicense =
    !!config?.licenseValid &&
    !!config?.validUntil &&
    config.validUntil > new Date();
  const [from, setFrom] = useState<Date | null>(null);
  const [to, setTo] = useState<Date | null>(null);
  const { executions, loading } = useExecutions(from, to);
  const [searchTerm, setSearchTerm] = useState("");
  const filteredExecutions = executions.filter(
    (execution) =>
      execution.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      execution.statement.toLowerCase().includes(searchTerm.toLowerCase()) ||
      execution.connectionId.toLowerCase().includes(searchTerm.toLowerCase()),
  );

  return (
    <div>
      <div className="my-4 flex flex-wrap items-center gap-2">
        <SearchInput
          value={searchTerm}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setSearchTerm(e.target.value);
          }}
          placeholder="Search Auditlog"
          className="min-w-40 flex-1 [&_input]:py-1"
        />
        <DateRangeFilter
          from={from}
          to={to}
          disabled={!hasEnterpriseLicense}
          onChange={(nextFrom, nextTo) => {
            setFrom(nextFrom);
            setTo(nextTo);
          }}
        />
        <ExportButton />
      </div>
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
        title={execution.executionTime.toLocaleString()}
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
            <p
              className="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400"
              title={execution.executionTime.toLocaleString()}
            >
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
