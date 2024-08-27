import { useState } from "react";
import { SelectExecuteResponse } from "../api/ExecutionRequestApi";

import {
  createColumnHelper,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";

type Person = {
  firstName: string;
  lastName: string;
  age: number;
  visits: number;
  status: string;
  progress: number;
};

const columnHelper = createColumnHelper<Person>();
const Table: React.FC<{ data: SelectExecuteResponse }> = ({ data }) => {
  const columns = data.columns.map((column) => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    return columnHelper.accessor(column.label as any, {
      header: column.label,
    });
  });
  const [selected, setSelected] = useState<boolean>(false);

  const table = useReactTable({
    data: data.data,
    // eslint-disable-next-line @typescript-eslint/no-explicit-any, @typescript-eslint/no-unsafe-assignment
    columns: columns as any,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div
      className={`block max-h-[calc(100vh-theme(spacing.32))] overflow-y-scroll px-2 font-thin ${
        selected ? "w-screen" : "w-full"
      } my-4 shrink-0 rounded border border-slate-300 shadow-md transition-width dark:border-slate-700 dark:shadow-none`}
    >
      <table className="w-full text-left">
        <thead
          className="sticky top-0 z-10 w-full cursor-pointer border-b border-slate-200 bg-slate-50 text-sm font-semibold leading-6 transition-colors hover:bg-slate-100 dark:border-slate-800 dark:bg-slate-950 dark:hover:bg-slate-900"
          onClick={() => setSelected(!selected)}
        >
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th
                  className="text-sx py-2 pr-2 text-slate-700 dark:text-slate-200"
                  key={header.id}
                  data-testid="result-table-header"
                >
                  {header.isPlaceholder
                    ? null
                    : flexRender(
                        header.column.columnDef.header,
                        header.getContext(),
                      )}
                </th>
              ))}
            </tr>
          ))}
        </thead>
        <tbody className="w-full">
          {table.getRowModel().rows.map((row) => (
            <tr
              key={row.id}
              className="transition-colors hover:bg-slate-200 dark:hover:bg-slate-800"
            >
              {row.getVisibleCells().map((cell) => (
                <td
                  className="text-sx whitespace-pre border-b
                  border-slate-200 py-2 pr-2 text-slate-700 dark:border-slate-800 dark:text-slate-200"
                  key={cell.id}
                  data-testid="result-table-cell"
                >
                  {flexRender(cell.column.columnDef.cell, cell.getContext())}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default Table;
