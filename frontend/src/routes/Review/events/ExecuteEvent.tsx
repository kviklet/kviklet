import { Execute } from "../../../api/ExecutionRequestApi";
import { timeSince } from "../../Requests";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import { Highlighter } from "../components/Highlighter";

function ExecuteEvent({ event, index }: { event: Execute; index: number }) {
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
          <div className="text-sm text-slate-500">
            {event?.author?.fullName} {sqlStatementText}
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
          <div className="mr-4">
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
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
        {event.results.length > 0 && (
          <div className="px-4 dark:bg-slate-900">
            <Disclosure defaultOpen={true}>
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
                      {event.results.map((result, index) => {
                        const renderResult = () => {
                          if (result.type === "QUERY") {
                            return (
                              <div className="flex justify-between">
                                <span>
                                  Returned {result.columnCount} Column(s) with{" "}
                                  {result.rowCount} row(s).
                                </span>
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
