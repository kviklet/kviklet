import React, { useState, ReactNode } from "react";
import { TrashIcon } from "@heroicons/react/24/outline";
import Button from "./Button";
import Modal from "./Modal";
import DeleteConfirm from "./DeleteConfirm";

export interface Column<T> {
  header: string;
  accessor?: keyof T | ((item: T) => ReactNode);
  render?: (item: T) => ReactNode;
  className?: string;
}

export interface SettingsTableProps<T> {
  title: string;
  data: T[];
  columns: Column<T>[];
  keyExtractor: (item: T) => string | number;
  onRowClick?: (item: T) => void;
  onDelete?: (item: T) => Promise<void>;
  canDelete?: (item: T) => boolean;
  onCreate?: () => void;
  createButtonLabel?: string;
  emptyMessage?: string;
  loading?: boolean;
  testId?: string;
}

function SettingsTable<T>({
  title,
  data,
  columns,
  keyExtractor,
  onRowClick,
  onDelete,
  canDelete,
  onCreate,
  createButtonLabel = "Create",
  emptyMessage = "No items found. Create one to get started.",
  loading = false,
  testId = "settings-table",
}: SettingsTableProps<T>) {
  const [deleteItem, setDeleteItem] = useState<T | null>(null);

  const handleDelete = async (item: T) => {
    if (!onDelete) return;

    try {
      await onDelete(item);
      setDeleteItem(null);
    } finally {
      // Delete completed
    }
  };

  const getCellValue = (item: T, column: Column<T>): ReactNode => {
    if (column.render) {
      return column.render(item);
    }

    if (column.accessor) {
      if (typeof column.accessor === "function") {
        return column.accessor(item);
      }
      return item[column.accessor] as ReactNode;
    }

    return null;
  };

  if (loading) {
    return (
      <div className="flex h-64 items-center justify-center">
        <div className="text-slate-500 dark:text-slate-400">Loading...</div>
      </div>
    );
  }

  return (
    <>
      <div className="mb-6">
        <div className="flex items-center justify-between">
          <h2 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
            {title}
          </h2>
          {onCreate && (
            <Button
              onClick={onCreate}
              variant="primary"
              dataTestId={`${testId}-create-button`}
            >
              {createButtonLabel}
            </Button>
          )}
        </div>
      </div>

      {data.length === 0 ? (
        <div className="flex h-64 items-center justify-center rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <p className="text-slate-500 dark:text-slate-400">{emptyMessage}</p>
        </div>
      ) : (
        <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow dark:border-slate-700 dark:bg-slate-900">
          <table className="min-w-full" data-testid={testId}>
            <thead className="bg-slate-50 dark:bg-slate-800">
              <tr>
                {columns.map((column, index) => (
                  <th
                    key={index}
                    className={`px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300 ${
                      column.className || ""
                    }`}
                  >
                    {column.header}
                  </th>
                ))}
                {onDelete && (
                  <th className="relative px-6 py-3">
                    <span className="sr-only">Actions</span>
                  </th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
              {data.map((item) => {
                const key = keyExtractor(item);
                const isClickable = !!onRowClick;

                return (
                  <tr
                    key={key}
                    onClick={() => onRowClick?.(item)}
                    className={`${
                      isClickable
                        ? "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800"
                        : ""
                    } transition-colors`}
                    data-testid={`${testId}-row-${key}`}
                  >
                    {columns.map((column, colIndex) => (
                      <td
                        key={colIndex}
                        className={`whitespace-nowrap px-6 py-4 text-sm text-slate-900 dark:text-slate-100 ${
                          column.className || ""
                        }`}
                      >
                        {getCellValue(item, column)}
                      </td>
                    ))}
                    {onDelete && (
                      <td className="whitespace-nowrap px-6 py-4 text-right text-sm font-medium">
                        {!canDelete || canDelete(item) ? (
                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              setDeleteItem(item);
                            }}
                            className="text-red-600 hover:text-red-900 dark:text-red-400 dark:hover:text-red-300"
                            title="Delete"
                            data-testid={`${testId}-delete-${key}`}
                          >
                            <TrashIcon className="h-5 w-5" />
                          </button>
                        ) : (
                          <button
                            disabled
                            className="cursor-not-allowed text-slate-400 dark:text-slate-600"
                            title="Cannot delete system role"
                          >
                            <TrashIcon className="h-5 w-5" />
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {deleteItem && onDelete && (
        <Modal setVisible={() => setDeleteItem(null)}>
          <DeleteConfirm
            title="Confirm Deletion"
            message="Are you sure you want to delete this item? This action cannot be undone."
            onConfirm={() => handleDelete(deleteItem)}
            onCancel={() => setDeleteItem(null)}
          />
        </Modal>
      )}
    </>
  );
}

export default SettingsTable;
