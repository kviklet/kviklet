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
}: {
  event: Review;
  connectTop?: boolean;
}) {
  const notificationText = (): ReactElement => {
    switch (event.action) {
      case "APPROVE":
        return (
          <div className="text-sm text-slate-500">
            {event.author?.fullName} approved
          </div>
        );
      case "REJECT":
        return (
          <div className="text-sm text-red-500">
            {event.author?.fullName} rejected
          </div>
        );
      case "REQUEST_CHANGE":
        return (
          <div className="text-sm text-red-500">
            {event.author?.fullName} requested changes
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
  return (
    <TimelineItem
      connectTop={connectTop}
      header={
        <div className="flex justify-center align-middle">
          {notificationIcon()}
          {notificationText()}
        </div>
      }
    >
      <div className="relative rounded-md border shadow-md dark:border-slate-700 dark:shadow-none">
        <InitialBubble name={event?.author?.fullName} />
        <p className="flex justify-between rounded-t-md px-4 pt-2 text-sm text-slate-500 dark:bg-slate-900 dark:text-slate-500">
          <div title={event?.createdAt?.toLocaleString()}>
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
    </TimelineItem>
  );
}

export default ReviewEvent;
