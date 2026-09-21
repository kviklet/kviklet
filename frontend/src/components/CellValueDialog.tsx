import { FC, useEffect, useRef, useState } from "react";
import { CheckIcon, ClipboardIcon, XMarkIcon } from "@heroicons/react/20/solid";
import IconButton from "./IconButton";

type CopyState = "idle" | "copied" | "failed";

// Shows one result cell in full: wrapped, selectable and copyable. It stacks
// above the expanded result view, so it must close on its own without
// collapsing that one.
const CellValueDialog: FC<{
  column: string;
  typeName: string;
  value: string;
  onClose: () => void;
}> = ({ column, typeName, value, onClose }) => {
  const closeRef = useRef<HTMLButtonElement>(null);
  const [copy, setCopy] = useState<CopyState>("idle");

  useEffect(() => {
    const previous = document.activeElement;
    closeRef.current?.focus();
    return () => {
      if (previous instanceof HTMLElement) previous.focus();
    };
  }, []);

  useEffect(() => {
    if (copy === "idle") return;
    const timer = setTimeout(() => setCopy("idle"), 2000);
    return () => clearTimeout(timer);
  }, [copy]);

  const copyValue = () => {
    navigator.clipboard.writeText(value).then(
      () => setCopy("copied"),
      () => setCopy("failed"),
    );
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-label={`Value of ${column}`}
      className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 p-4 dark:bg-black/70 sm:p-8"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
      onKeyDown={(event) => {
        if (event.key !== "Escape") return;
        event.stopPropagation();
        onClose();
      }}
      data-testid="cell-value-dialog"
    >
      <div className="flex max-h-full w-full max-w-2xl flex-col overflow-hidden rounded border border-slate-300 bg-white shadow-lg dark:border-slate-700 dark:bg-slate-950">
        <div className="flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-2 py-1.5 text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400">
          <span className="min-w-0 truncate pl-1 font-semibold text-slate-700 dark:text-slate-200">
            {column}
            <span className="ml-1.5 font-mono text-[11px] font-normal lowercase text-slate-400 dark:text-slate-500">
              {typeName}
            </span>
          </span>
          <span className="hidden whitespace-nowrap tabular-nums sm:inline">
            {value.length.toLocaleString()} chars
          </span>
          <button
            type="button"
            onClick={copyValue}
            data-testid="cell-value-copy"
            className="ml-auto flex items-center gap-1 rounded px-1.5 py-0.5 text-xs text-slate-500 transition-colors hover:bg-slate-200 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-700 dark:hover:text-slate-50"
          >
            {copy === "copied" ? (
              <CheckIcon className="h-4 w-4" />
            ) : (
              <ClipboardIcon className="h-4 w-4" />
            )}
            {copy === "copied"
              ? "Copied"
              : copy === "failed"
              ? "Copy failed"
              : "Copy"}
          </button>
          <IconButton
            ref={closeRef}
            label="Close"
            onClick={onClose}
            testId="cell-value-close"
            className="ml-1"
          >
            <XMarkIcon className="h-4 w-4" />
          </IconButton>
        </div>
        <pre className="min-h-0 flex-1 select-text overflow-auto whitespace-pre-wrap break-all p-3 font-mono text-xs text-slate-700 scrollbar-thin scrollbar-track-transparent scrollbar-thumb-slate-300 scrollbar-thumb-rounded-full dark:text-slate-200 dark:scrollbar-thumb-slate-700">
          {value}
        </pre>
      </div>
    </div>
  );
};

export default CellValueDialog;
