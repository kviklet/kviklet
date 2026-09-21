import { FC, useState } from "react";
import { Column, SelectExecuteResponse } from "../api/ExecutionRequestApi";
import CellValueDialog from "./CellValueDialog";

// Long values are clipped so one wide cell cannot stretch the whole grid. Any
// value that might be clipped opens in full on click; short single-line ones
// are shown as they are and stay plain text.
const TRUNCATE_HINT_LENGTH = 40;

const mayBeClipped = (value: string) =>
  value.length > TRUNCATE_HINT_LENGTH || value.includes("\n");

function Cell({
  value,
  onOpen,
}: {
  value: string | null;
  onOpen: (value: string) => void;
}) {
  if (value === null) {
    return (
      <span className="italic text-slate-400 dark:text-slate-500">NULL</span>
    );
  }
  if (!mayBeClipped(value)) {
    return <div className="max-w-md truncate">{value}</div>;
  }
  return (
    <button
      type="button"
      onClick={() => onOpen(value)}
      title="Show full value"
      data-testid="result-table-cell-open"
      className="block max-w-md cursor-pointer truncate rounded text-left focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-indigo-500 hover:text-indigo-700 dark:hover:text-indigo-300"
    >
      {value}
    </button>
  );
}

// Mirrors the editor above it: a numbered gutter on the left and a quiet
// sticky header, so the query and its answer read as one workspace.
const Table: FC<{ data: SelectExecuteResponse; className?: string }> = ({
  data,
  className = "",
}) => {
  const [detail, setDetail] = useState<{
    column: Column;
    value: string;
  } | null>(null);

  return (
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
                  <Cell
                    value={row[column.label] ?? null}
                    onOpen={(value) => setDetail({ column, value })}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
      {detail && (
        <CellValueDialog
          column={detail.column.label}
          typeName={detail.column.typeName}
          value={detail.value}
          onClose={() => setDetail(null)}
        />
      )}
    </div>
  );
};

export default Table;
