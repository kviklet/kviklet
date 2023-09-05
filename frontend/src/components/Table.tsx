import { useContext, useReducer, useState } from "react";
import { Column, SelectExecuteResponse } from "../api/ExecutionRequestApi";

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

const defaultData: Person[] = [
  {
    firstName: "tanner",
    lastName: "linsley",
    age: 24,
    visits: 100,
    status: "In Relationship",
    progress: 50,
  },
  {
    firstName: "tandy",
    lastName: "miller",
    age: 40,
    visits: 40,
    status: "Single",
    progress: 80,
  },
  {
    firstName: "joe",
    lastName: "dirte",
    age: 45,
    visits: 20,
    status: "Complicated",
    progress: 10,
  },
];

const columnHelper = createColumnHelper<Person>();

const columns = [
  columnHelper.accessor("firstName", {
    cell: (info) => info.getValue(),
    footer: (info) => info.column.id,
  }),
  columnHelper.accessor((row) => row.lastName, {
    id: "lastName",
    cell: (info) => <i>{info.getValue()}</i>,
    header: () => <span>Last Name</span>,
    footer: (info) => info.column.id,
  }),
  columnHelper.accessor("age", {
    header: () => "Age",
    cell: (info) => info.renderValue(),
    footer: (info) => info.column.id,
  }),
  columnHelper.accessor("visits", {
    header: () => <span>Visits</span>,
    footer: (info) => info.column.id,
  }),
  columnHelper.accessor("status", {
    header: "Status",
    footer: (info) => info.column.id,
  }),
  columnHelper.accessor("progress", {
    header: "Profile Progress",
    footer: (info) => info.column.id,
  }),
];

const Table: React.FC<{ data: SelectExecuteResponse }> = ({ data }) => {
  const columns = data.columns.map((column) => {
    return columnHelper.accessor(column.label as any, {
      header: column.label,
    });
  });
  const [selected, setSelected] = useState<boolean>(false);

  const table = useReactTable({
    data: data.data,
    columns: columns as any,
    getCoreRowModel: getCoreRowModel(),
  });

  return (
    <div
      className={`px-2 h-[calc(100vh-theme(spacing.16))] overflow-y-scroll block font-thin ${
        selected ? "w-screen" : "w-full"
      } transition-width border shrink-0 rounded border-slate-300 shadow-md dark:shadow-none dark:border-slate-700 my-4`}
    >
      <table className="text-left w-full">
        <thead
          className="sticky z-10 top-0 text-sm leading-6 font-semibold dark:bg-slate-950 dark:hover:bg-slate-900 hover:bg-slate-100 bg-slate-50 w-full border-b border-slate-200 dark:border-slate-800 transition-colors cursor-pointer"
          onClick={() => setSelected(!selected)}
        >
          {table.getHeaderGroups().map((headerGroup) => (
            <tr key={headerGroup.id}>
              {headerGroup.headers.map((header) => (
                <th
                  className="py-2 pr-2 text-sx text-slate-700 dark:text-slate-200"
                  key={header.id}
                >
                  {header.isPlaceholder
                    ? null
                    : flexRender(
                        header.column.columnDef.header,
                        header.getContext()
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
              className="hover:bg-slate-200 dark:hover:bg-slate-800 transition-colors"
            >
              {row.getVisibleCells().map((cell) => (
                <td
                  className="border-b dark:border-slate-800 border-slate-200
                  py-2 pr-2 text-sx text-slate-700 dark:text-slate-200 whitespace-pre"
                  key={cell.id}
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
