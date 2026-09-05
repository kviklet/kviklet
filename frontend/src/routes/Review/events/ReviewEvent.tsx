import ReactMarkdown from "react-markdown";
import { timeSince } from "../../Requests";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { Review } from "../../../api/ExecutionRequestApi";
import { componentMap } from "../components/Highlighter";
import { ReactElement } from "react";
import TimelineItem from "./TimelineItem";

function ReviewEvent({
  event,
  connectTop,
  connectBottom,
}: {
  event: Review;
  connectTop?: boolean;
  connectBottom?: boolean;
}) {
  const timestamp = event?.createdAt && (
    <span
      className="text-slate-400 dark:text-slate-600"
      title={event.createdAt.toLocaleString()}
    >
      {" "}
      · {timeSince(event.createdAt)}
    </span>
  );

  const notificationText = (): ReactElement => {
    switch (event.action) {
      case "APPROVE":
        return (
          <div className="text-sm text-slate-500">
            {event.author?.fullName} approved
            {timestamp}
          </div>
        );
      case "REJECT":
        return (
          <div className="text-sm text-red-500">
            {event.author?.fullName} rejected
            {timestamp}
          </div>
        );
      case "REQUEST_CHANGE":
        return (
          <div className="text-sm text-red-500">
            {event.author?.fullName} requested changes
            {timestamp}
          </div>
        );
    }
  };

  const notificationIcon = (): ReactElement => {
    switch (event.action) {
      case "APPROVE":
        return (
          <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 align-text-bottom dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-green-600">
              <FontAwesomeIcon icon={solid("check")} />
            </div>
          </div>
        );
      case "REJECT":
        return (
          <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-red-500">
              <FontAwesomeIcon icon={solid("times")} />
            </div>
          </div>
        );
      case "REQUEST_CHANGE":
        return (
          <div className="z-0 -ml-1 mr-2 inline-block h-4 w-4 items-center bg-slate-50 fill-slate-950 pb-6 dark:bg-slate-950 dark:fill-slate-50">
            <div className="inline pr-2 text-red-500">
              <FontAwesomeIcon icon={solid("pen")} />
            </div>
          </div>
        );
    }
  };
  // A review without a comment (e.g. a plain approval from the sidebar) is
  // just the timeline notice — no empty comment card below it. The timestamp
  // lives in the notice, so the card holds only the comment itself.
  return (
    <TimelineItem
      connectTop={connectTop}
      connectBottom={connectBottom}
      header={
        <div className="flex justify-center align-middle">
          {notificationIcon()}
          {notificationText()}
        </div>
      }
    >
      {event.comment.trim() !== "" && (
        <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
          <InitialBubble name={event?.author?.fullName} />
          <div className="rounded-md px-4 py-3 dark:bg-slate-900">
            <ReactMarkdown components={componentMap}>
              {event.comment}
            </ReactMarkdown>
          </div>
        </div>
      )}
    </TimelineItem>
  );
}

export default ReviewEvent;
