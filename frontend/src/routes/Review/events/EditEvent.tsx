import { Edit } from "../../../api/ExecutionRequestApi";
import { timeSince } from "../../Requests";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { Highlighter } from "../components/Highlighter";

function EditEvent({ event, index }: { event: Edit; index: number }) {
  return (
    <div>
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
        <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
          <div className="inline pr-2 text-xs text-slate-900 dark:text-slate-500">
            <FontAwesomeIcon icon={solid("pen")} />
          </div>
        </div>
        <div className="text-sm text-slate-500">
          {event?.author?.fullName} edited:
        </div>
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <p className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div className="mr-4">
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
          {event?.previousQuery && (
            <div>
              <p>Previous Statement</p>
            </div>
          )}
          {event?.previousCommand && (
            <div>
              <p>Previous Command</p>
            </div>
          )}
        </p>
        {event?.previousQuery && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.previousQuery}</Highlighter>
          </div>
        )}
        {event?.previousCommand && (
          <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
            <Highlighter>{event.previousCommand}</Highlighter>
          </div>
        )}
      </div>
    </div>
  );
}

export default EditEvent;
