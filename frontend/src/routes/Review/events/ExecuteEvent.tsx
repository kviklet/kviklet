import { Execute } from "../../../api/ExecutionRequestApi";
import { DatabaseType } from "../../../api/DatasourceApi";
import { timeSince } from "../../Requests";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { Highlighter } from "../components/Highlighter";
import TimelineItem from "./TimelineItem";
import ExecuteEventResults from "./ExecuteEventResults";
import UserName from "../../../components/UserName";
import ViaSource from "../../../components/ViaSource";

function ExecuteEvent({
  event,
  connectTop,
  connectionType,
}: {
  event: Execute;
  connectTop?: boolean;
  connectionType?: DatabaseType;
}) {
  const isDownload = event.isDownload || false;
  const sqlStatementText = isDownload
    ? "downloaded the results for the following statement:"
    : "executed the following statement:";
  return (
    <TimelineItem
      connectTop={connectTop}
      header={
        <>
          <div className="z-0 -ml-0.5 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-xs text-green-600">
              <FontAwesomeIcon icon={solid("play")} />
            </div>
          </div>
          {event?.isDump && (
            <div className="text-sm text-slate-500">
              {event?.author && <UserName user={event.author} />} requested a
              SQL dump from the database.
            </div>
          )}
          {event?.query && (
            <div className="flex items-center text-sm text-slate-500">
              <span>
                {event?.author && <UserName user={event.author} />}{" "}
                {sqlStatementText}
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
              {event?.author && <UserName user={event.author} />} ran the
              following command:
            </div>
          )}
        </>
      }
    >
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:bg-slate-900 dark:shadow-none">
        <InitialBubble
          name={event?.author?.fullName}
          muted={event?.author?.active === false}
        />
        <div className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div className="mr-4" title={event?.createdAt?.toLocaleString()}>
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
            <ViaSource source={event?.source} />
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
        {event.results && event.results.length > 0 && (
          <ExecuteEventResults event={event} connectionType={connectionType} />
        )}
      </div>
    </TimelineItem>
  );
}

export default ExecuteEvent;
