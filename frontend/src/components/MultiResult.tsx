import { FC, ReactNode, useEffect, useRef, useState } from "react";
import {
  ArrowsPointingInIcon,
  ArrowsPointingOutIcon,
  ChevronLeftIcon,
  ChevronRightIcon,
} from "@heroicons/react/20/solid";
import { ExecuteResponseResult } from "../api/ExecutionRequestApi";
import Table from "./Table";
import JsonViewer from "./JsonViewer";

const plural = (count: number, noun: string) =>
  `${count} ${noun}${count === 1 ? "" : "s"}`;

const summarize = (result: ExecuteResponseResult) => {
  switch (result._type) {
    case "select":
      return `${plural(result.data.length, "row")} · ${plural(
        result.columns.length,
        "column",
      )}`;
    case "update":
      return "Update";
    case "error":
      return result.errorCode ? `Error ${result.errorCode}` : "Error";
    case "documents":
      return plural(result.documents.length, "document");
  }
};

const IconButton = ({
  label,
  onClick,
  disabled,
  children,
  testId,
}: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
  testId?: string;
}) => (
  <button
    type="button"
    aria-label={label}
    title={label}
    disabled={disabled}
    onClick={onClick}
    data-testid={testId}
    className="rounded p-0.5 text-slate-500 transition-colors disabled:cursor-default disabled:text-slate-300 hover:bg-slate-200 hover:text-slate-900 disabled:hover:bg-transparent dark:text-slate-400 dark:disabled:text-slate-700 dark:hover:bg-slate-700 dark:hover:text-slate-50"
  >
    {children}
  </button>
);

// The panel borrows the editor's chrome so query and result read as one
// workspace. Wide result sets get an explicit expand into a full-viewport
// view instead of a hidden click on the header.
const MultiResult: FC<{ resultList: ExecuteResponseResult[] }> = ({
  resultList,
}) => {
  const [currentIndex, setCurrentIndex] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const collapseRef = useRef<HTMLButtonElement>(null);
  const index = Math.min(currentIndex, resultList.length - 1);
  const result = resultList[index];
  const expandable = result._type === "select" || result._type === "documents";

  useEffect(() => {
    if (!expanded) return;
    collapseRef.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setExpanded(false);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [expanded]);

  const body = (() => {
    switch (result._type) {
      case "select":
        return (
          <Table
            data={result}
            className={expanded ? "min-h-0 flex-1" : "max-h-[28rem]"}
          />
        );
      case "update":
        return (
          <div className="px-3 py-3 text-sm text-slate-700 dark:text-slate-200">
            {plural(result.rowsUpdated, "row")} updated
          </div>
        );
      case "error":
        return (
          <div className="whitespace-pre-wrap px-3 py-3 font-mono text-xs text-red-600 dark:text-red-400">
            {result.message}
          </div>
        );
      case "documents":
        return (
          <div
            className={`overflow-auto p-3 ${
              expanded ? "min-h-0 flex-1" : "max-h-[28rem]"
            }`}
          >
            <JsonViewer data={result.documents} />
          </div>
        );
    }
  })();

  const panel = (
    <div
      className={`flex flex-col overflow-hidden rounded border border-slate-300 bg-white dark:border-slate-700 dark:bg-slate-950 ${
        expanded ? "max-h-full" : ""
      }`}
      data-testid="result-component"
    >
      <div className="flex items-center gap-3 border-b border-slate-200 bg-slate-50 px-2 py-1.5 text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
        {resultList.length > 1 && (
          <div className="flex items-center gap-1">
            <IconButton
              label="Previous result"
              onClick={() => setCurrentIndex(index - 1)}
              disabled={index === 0}
            >
              <ChevronLeftIcon className="h-4 w-4" />
            </IconButton>
            <span className="tabular-nums">
              {index + 1} / {resultList.length}
            </span>
            <IconButton
              label="Next result"
              onClick={() => setCurrentIndex(index + 1)}
              disabled={index === resultList.length - 1}
            >
              <ChevronRightIcon className="h-4 w-4" />
            </IconButton>
          </div>
        )}
        <span
          className={`min-w-0 truncate pl-1 ${
            result._type === "error" ? "text-red-600 dark:text-red-400" : ""
          }`}
        >
          {summarize(result)}
        </span>
        {expandable && (
          <button
            ref={expanded ? collapseRef : undefined}
            type="button"
            onClick={() => setExpanded(!expanded)}
            data-testid="result-expand-button"
            className="ml-auto flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-slate-500 transition-colors hover:bg-slate-200 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-50"
          >
            {expanded ? (
              <ArrowsPointingInIcon className="h-4 w-4" />
            ) : (
              <ArrowsPointingOutIcon className="h-4 w-4" />
            )}
            {expanded ? "Collapse" : "Expand"}
          </button>
        )}
      </div>
      {body}
    </div>
  );

  if (!expanded) return panel;
  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label="Query results"
      className="fixed inset-0 z-50 bg-slate-900/60 p-4 dark:bg-black/70 sm:p-8"
      onClick={(event) => {
        if (event.target === event.currentTarget) setExpanded(false);
      }}
    >
      {panel}
    </div>
  );
};

export default MultiResult;
