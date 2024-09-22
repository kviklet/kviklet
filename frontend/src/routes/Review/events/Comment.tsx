import ReactMarkdown from "react-markdown";
import {
  Review,
  Comment as CommentEvent,
} from "../../../api/ExecutionRequestApi";
import { timeSince } from "../../Requests";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { componentMap } from "../components/Highlighter";

function Comment({
  event,
  index,
}: {
  event: Review | CommentEvent;
  index: number;
}) {
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
        {event?._type === "COMMENT" ? (
          <svg className="z-0 -ml-2 mr-2 mt-0.5 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <path d="M11.93 8.5a4.002 4.002 0 0 1-7.86 0H.75a.75.75 0 0 1 0-1.5h3.32a4.002 4.002 0 0 1 7.86 0h3.32a.75.75 0 0 1 0 1.5Zm-1.43-.75a2.5 2.5 0 1 0-5 0 2.5 2.5 0 0 0 5 0Z"></path>
          </svg>
        ) : (
          <div className="z-0 -ml-1 mr-2 mt-0.5 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-green-600">
              <FontAwesomeIcon icon={solid("check")} />
            </div>
          </div>
        )}
        <div className="text-sm text-slate-500">
          {event?._type === "COMMENT" ? (
            <div>{`${event?.author?.fullName} commented:`}</div>
          ) : (
            <div>{`${event?.author?.fullName} approved`} </div>
          )}
        </div>
      </div>
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <p className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div>
            {((event?.createdAt && timeSince(event.createdAt)) as
              | string
              | undefined) || ""}
          </div>
        </p>
        <div className="rounded-b-md px-4 py-3 dark:bg-slate-900">
          <ReactMarkdown components={componentMap}>
            {event.comment}
          </ReactMarkdown>
        </div>
      </div>
    </div>
  );
}

export default Comment;
