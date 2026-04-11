import { Execute } from "../../../api/ExecutionRequestApi";
import { DatabaseType } from "../../../api/DatasourceApi";
import useTimezone from "../../../hooks/useTimezone";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import { Highlighter } from "../components/Highlighter";
import JsonViewer from "../../../components/JsonViewer";

function ExecuteEvent({
  event,
  index,
  connectionType,
}: {
  event: Execute;
  index: number;
  connectionType?: DatabaseType;
}) {
  const { formatTime } = useTimezone();
  const isDownload = event.isDownload || false;
  const sqlStatementText = isDownload
    ? "downloaded the results for the following statement:"
    : "executed the following statement:";
  return (
    <div className="">
      <div className="relative ml-4 flex py-4">
        {(!(index === 0) && (
          <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )) || (
          <div className="absolute bottom-0 left-0 top-5 block w-0.5 whitespace-pre bg-slate-700">
            {" "}
          </div>
        )}
        <div className="z-0 -ml-0.5 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
          <div className="inline pr-2 text-xs text-green-600">
            <FontAwesomeIcon icon={solid("play")} />
          </div>
        </div>
        {event?.isDump && (
          <div className="text-sm text-slate-500">
            {event?.author?.fullName} requested a SQL dump from the database.
          </div>
        )}
        {event?.query && (
          <div className="flex items-center text-sm text-slate-500">
            <span>
              {event?.author?.fullName} {sqlStatementText}
            </span>
            {event?.isDryRun && (
              <span className="ml-2 rounded bg-yellow-100 px-2 py-0.5 text-xs font-medium text-yellow-800 dark:bg-yellow-900 dark:text-yellow-200">
                Dry Run
              </span>
            )}
          </div>
        )}
        {event?.command && (
          <div className="text-sm text-slate-500">
            {event?.author?.fullName} ran the following command:
          </div>
        )}
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:bg-slate-900 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <div className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <span className="mr-4">
            {event?.createdAt
              ? formatTime(event.createdAt)
              : ""}
          </span>
        </div>
        {event?.query && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.query}</Highlighter>
          </div>
        )}

        {event?.command && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.command}</Highlighter>
          </div>
        )}
        {event.results && event.results.length > 0 && (
          <div className="px-4 dark:bg-slate-900">
            <Disclosure>
              {({ open }) => (
                <>
                  <Disclosure.Button className="w-full py-2 ">
                    <div className="flex w-full flex-row justify-start">
                      <p className="text-xs">Results</p>
                      {open ? (
                        <ChevronDownIcon className="h-4 w-4 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                      ) : (
                        <ChevronRightIcon className="h-4 w-4 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                      )}
                    </div>
                  </Disclosure.Button>
                  <Disclosure.Panel>
                    <div className="mb-2 flex flex-col space-y-2 text-sm dark:text-slate-300">
                      {event.results?.map((result, index) => {
                        const renderResult = () => {
                          if (result.type === "QUERY") {
                            return (
                              <div>
                                <div className="flex justify-between">
                                  <span>
                                    Returned {result.columnCount} Column(s) with{" "}
                                    {result.rowCount} row(s).
                                  </span>
                                </div>
                                {result.storedRows &&
                                  result.storedRows.length > 0 && (
                                    <>
                                      <div className="mt-1 text-xs text-slate-500 dark:text-slate-400">
                                        Stored {result.storedRowCount} of{" "}
                                        {result.rowCount} rows
                                      </div>
                                      {connectionType ===
                                      DatabaseType.MONGODB ? (
                                        <div className="mt-2">
                                          <JsonViewer
                                            data={result.storedRows}
                                          />
                                        </div>
                                      ) : (
                                        <div className="mt-2 max-h-96 overflow-auto rounded border border-slate-200 dark:border-slate-700">
                                          <table className="min-w-full text-xs">
                                            <thead className="bg-slate-100 dark:bg-slate-800">
                                              <tr>
                                                {result.columns?.map(
                                                  (col, i) => (
                                                    <th
                                                      key={i}
                                                      className="px-2 py-1 text-left font-medium text-slate-600 dark:text-slate-300"
                                                    >
                                                      {col.label}
                                                    </th>
                                                  ),
                                                )}
                                              </tr>
                                            </thead>
                                            <tbody>
                                              {result.storedRows?.map(
                                                (row, i) => (
                                                  <tr
                                                    key={i}
                                                    className="border-t border-slate-200 dark:border-slate-700"
                                                  >
                                                    {result.columns?.map(
                                                      (col, j) => (
                                                        <td
                                                          key={j}
                                                          className="px-2 py-1 text-slate-700 dark:text-slate-300"
                                                        >
                                                          {row[col.label]}
                                                        </td>
                                                      ),
                                                    )}
                                                  </tr>
                                                ),
                                              )}
                                            </tbody>
                                          </table>
                                        </div>
                                      )}
                                    </>
                                  )}
                              </div>
                            );
                          } else if (result.type === "ERROR") {
                            return (
                              <div className="flex justify-between text-red-500">
                                <span>
                                  Query resulted in Error "{result.message}""
                                  with code "{result.errorCode}"".
                                </span>
                              </div>
                            );
                          } else if (result.type === "UPDATE") {
                            return (
                              <div className="flex justify-between">
                                <span>
                                  Statement affected {result.rowsUpdated}{" "}
                                  row(s).
                                </span>
                              </div>
                            );
                          } else if (result.type === "KUBERNETES_OUTPUT") {
                            return (
                              <div>
                                {result.storedOutput && (
                                  <>
                                    <div className="text-xs text-slate-500 dark:text-slate-400">
                                      Output
                                      {result.outputTruncated && " (truncated)"}
                                      :
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
                                      {result.outputTruncated && " (truncated)"}
                                      :
                                    </div>
                                    <pre className="mt-1 max-h-96 overflow-auto whitespace-pre-wrap rounded bg-red-50 p-2 text-xs text-red-700 dark:bg-red-900/20 dark:text-red-400">
                                      {result.storedErrors}
                                    </pre>
                                  </>
                                )}
                                {result.exitCode !== null &&
                                  result.exitCode !== undefined && (
                                    <div className="mt-2 text-xs text-slate-500">
                                      Exit code: {result.exitCode}
                                    </div>
                                  )}
                              </div>
                            );
                          } else {
                            const _exhaustiveCheck: never = result;
                            return _exhaustiveCheck;
                          }
                        };
                        return <div key={index}>{renderResult()}</div>;
                      })}
                    </div>
                  </Disclosure.Panel>
                </>
              )}
            </Disclosure>
          </div>
        )}
      </div>
    </div>
  );
}

export default ExecuteEvent;
