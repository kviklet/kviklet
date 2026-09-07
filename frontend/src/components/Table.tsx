import { FC } from "react";
import { SelectExecuteResponse } from "../api/ExecutionRequestApi";

// Long values are clipped so one wide cell cannot stretch the whole grid; the
// full value stays reachable through the tooltip.
const TRUNCATE_HINT_LENGTH = 40;

function Cell({ value }: { value: string | null }) {
  if (value === null) {
    return (
      <span className="italic text-slate-400 dark:text-slate-500">NULL</span>
    );
  }
  return (
    <div
      className="max-w-md truncate"
      title={value.length > TRUNCATE_HINT_LENGTH ? value : undefined}
    >
      {value}
    </div>
  );
}

// Mirrors the editor above it: a numbered gutter on the left and a quiet
// sticky header, so the query and its answer read as one workspace.
const Table: FC<{ data: SelectExecuteResponse; className?: string }> = ({
  data,
  className = "",
}) => (
  <div
    className={`overflow-auto scrollbar-thin scrollbar-track-transparent scrollbar-thumb-slate-300 scrollbar-thumb-rounded-full dark:scrollbar-thumb-slate-700 ${className}`}
  >
    <table className="min-w-full border-separate border-spacing-0 text-left text-sm">
      <thead className="sticky top-0 z-10">
        <tr>
          <th
            className="w-px border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-900"
            aria-label="Row"
          />
          {data.columns.map((column) => (
            <th
              key={column.label}
              className="whitespace-nowrap border-b border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200"
              data-testid="result-table-header"
            >
              {column.label}
              <span className="ml-1.5 font-mono text-[11px] font-normal lowercase text-slate-400 dark:text-slate-500">
                {column.typeName}
              </span>
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {data.data.map((row, index) => (
          <tr
            key={index}
            className="group transition-colors hover:bg-slate-100 dark:hover:bg-slate-800/60"
          >
            <td className="select-none border-b border-slate-100 py-1.5 pl-3 pr-2 text-right font-mono text-xs tabular-nums text-slate-400 dark:border-slate-800 dark:text-slate-600">
              {index + 1}
            </td>
            {data.columns.map((column) => (
              <td
                key={column.label}
                className="whitespace-pre border-b border-slate-100 px-3 py-1.5 font-mono text-xs text-slate-700 dark:border-slate-800 dark:text-slate-200"
                data-testid="result-table-cell"
              >
                <Cell value={row[column.label] ?? null} />
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);

export default Table;
