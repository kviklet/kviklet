import { ReactNode } from "react";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import { CheckCircleIcon, XCircleIcon } from "@heroicons/react/24/outline";
import { Execute, ExecutionMessage } from "../../../api/ExecutionRequestApi";
import { DatabaseType } from "../../../api/DatasourceApi";
import JsonViewer from "../../../components/JsonViewer";
import ExecutionMessages from "../../../components/ExecutionMessages";

type ResultLog = NonNullable<Execute["results"]>[number];

const plural = (count: number, noun: string) =>
  `${count.toLocaleString()} ${noun}${count === 1 ? "" : "s"}`;

// Shown on the collapsed Results line, so a statement the database skipped
// with a warning is visible without opening it.
const summarizeMessages = (messages: ExecutionMessage[], total: number) => {
  if (total === 0) return undefined;
  const warnings = messages.filter((m) => m.level === "WARNING").length;
  const notices = messages.length - warnings;
  const text =
    total > messages.length
      ? plural(total, "message")
      : [
          warnings > 0 && plural(warnings, "warning"),
          notices > 0 && plural(notices, "notice"),
        ]
          .filter(Boolean)
          .join(" · ");
  return { text, hasWarning: warnings > 0 };
};

function StatementLine({
  number,
  failed,
  children,
}: {
  number?: number;
  failed?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex items-start gap-2">
      {number !== undefined && (
        <span className="w-4 flex-none text-right font-mono text-[11px] leading-5 text-slate-400 dark:text-slate-500">
          {number}
        </span>
      )}
      {failed ? (
        <XCircleIcon className="mt-0.5 h-4 w-4 flex-none text-red-700 dark:text-red-300" />
      ) : (
        <CheckCircleIcon className="mt-0.5 h-4 w-4 flex-none text-emerald-600 dark:text-emerald-400" />
      )}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">{children}</div>
    </div>
  );
}

function StoredRowsTable({
  result,
  connectionType,
}: {
  result: Extract<ResultLog, { type: "QUERY" }>;
  connectionType?: DatabaseType;
}) {
  if (!result.storedRows || result.storedRows.length === 0) return null;
  return (
    <>
      <div className="text-xs text-slate-500 dark:text-slate-400">
        Stored {result.storedRowCount} of {result.rowCount} rows
      </div>
      {connectionType === DatabaseType.MONGODB ? (
        <div className="mt-2">
          <JsonViewer data={result.storedRows} />
        </div>
      ) : (
        <div className="mt-2 max-h-96 overflow-auto rounded border border-slate-200 dark:border-slate-700">
          <table className="min-w-full text-xs">
            <thead className="bg-slate-100 dark:bg-slate-800">
              <tr>
                {result.columns?.map((col, i) => (
                  <th
                    key={i}
                    className="px-2 py-1 text-left font-medium text-slate-600 dark:text-slate-300"
                  >
                    {col.label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {result.storedRows.map((row, i) => (
                <tr
                  key={i}
                  className="border-t border-slate-200 dark:border-slate-700"
                >
                  {result.columns?.map((col, j) => (
                    <td
                      key={j}
                      className="px-2 py-1 text-slate-700 dark:text-slate-300"
                    >
                      {row[col.label] === null ? (
                        <span className="italic text-slate-400 dark:text-slate-500">
                          NULL
                        </span>
                      ) : (
                        row[col.label]
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function KubernetesOutput({
  result,
}: {
  result: Extract<ResultLog, { type: "KUBERNETES_OUTPUT" }>;
}) {
  return (
    <div>
      {result.storedOutput && (
        <>
          <div className="text-xs text-slate-500 dark:text-slate-400">
            Output
            {result.outputTruncated && " (truncated)"}:
          </div>
          <pre className="mt-1 max-h-96 overflow-auto whitespace-pre-wrap rounded bg-slate-100 p-2 text-xs text-slate-700 dark:bg-slate-800 dark:text-slate-300">
            {result.storedOutput}
          </pre>
        </>
      )}
      {result.storedErrors && (
        <>
          <div className="mt-2 text-xs text-red-600 dark:text-red-400">
            Errors
            {result.outputTruncated && " (truncated)"}:
          </div>
          <pre className="mt-1 max-h-96 overflow-auto whitespace-pre-wrap rounded bg-red-50 p-2 text-xs text-red-700 dark:bg-red-900/20 dark:text-red-400">
            {result.storedErrors}
          </pre>
        </>
      )}
      {result.exitCode !== null && result.exitCode !== undefined && (
        <div className="mt-2 text-xs text-slate-500">
          Exit code: {result.exitCode}
        </div>
      )}
    </div>
  );
}

function ExecuteEventResults({
  event,
  connectionType,
}: {
  event: Execute;
  connectionType?: DatabaseType;
}) {
  const results = event.results ?? [];
  const messages = event.messages ?? [];
  const messageTotal = event.messageCount ?? messages.length;
  const summary = summarizeMessages(messages, messageTotal);
  // Numbers only help to match lines to statements when there are several
  const numberOf = (index: number) =>
    results.length > 1 ? index + 1 : undefined;

  const renderResult = (result: ResultLog, index: number) => {
    switch (result.type) {
      case "QUERY":
        return (
          <StatementLine number={numberOf(index)}>
            <span>
              Returned {plural(result.rowCount, "row")} ·{" "}
              {plural(result.columnCount, "column")}
            </span>
            <StoredRowsTable result={result} connectionType={connectionType} />
          </StatementLine>
        );
      case "UPDATE":
        return (
          <StatementLine number={numberOf(index)}>
            <span>
              Statement executed · {plural(result.rowsUpdated, "row")} affected
            </span>
          </StatementLine>
        );
      case "ERROR":
        return (
          <StatementLine number={numberOf(index)} failed>
            <span className="text-red-700 dark:text-red-300">
              Statement failed
              {result.sqlState && ` · SQLSTATE ${result.sqlState}`}
            </span>
            <pre className="whitespace-pre-wrap break-words font-mono text-xs text-red-900 dark:text-red-200">
              {result.message}
            </pre>
          </StatementLine>
        );
      case "KUBERNETES_OUTPUT":
        return <KubernetesOutput result={result} />;
      default: {
        const _exhaustiveCheck: never = result;
        return _exhaustiveCheck;
      }
    }
  };

  return (
    <div className="px-4 dark:bg-slate-900">
      <Disclosure>
        {({ open }) => (
          <>
            <Disclosure.Button className="w-full py-2">
              <div className="flex w-full flex-row items-center justify-start gap-1.5 text-xs">
                <span>Results</span>
                {summary && (
                  <>
                    <span className="text-slate-400 dark:text-slate-500">
                      ·
                    </span>
                    <span
                      data-testid="execute-event-message-summary"
                      className={
                        summary.hasWarning
                          ? "font-medium text-amber-700 dark:text-amber-400"
                          : "text-slate-500 dark:text-slate-400"
                      }
                    >
                      {summary.text}
                    </span>
                  </>
                )}
                {open ? (
                  <ChevronDownIcon className="h-4 w-4 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                ) : (
                  <ChevronRightIcon className="h-4 w-4 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                )}
              </div>
            </Disclosure.Button>
            <Disclosure.Panel>
              <div className="mb-2 flex flex-col space-y-2 text-sm dark:text-slate-300">
                {results.map((result, index) => (
                  <div key={index}>{renderResult(result, index)}</div>
                ))}
                {messages.length > 0 && (
                  <div className="border-t border-slate-100 pt-2.5 dark:border-slate-800">
                    <ExecutionMessages
                      messages={messages}
                      total={messageTotal}
                    />
                  </div>
                )}
              </div>
            </Disclosure.Panel>
          </>
        )}
      </Disclosure>
    </div>
  );
}

export default ExecuteEventResults;
