import {
  ExecutionRequestResponseWithComments,
  Execute,
} from "../../api/ExecutionRequestApi";
import { DatabaseType } from "../../api/DatasourceApi";
import { ReviewTypes } from "../../hooks/request";
import CommentBox from "./CommentBox";
import EditEvent from "./events/EditEvent";
import ExecuteEvent from "./events/ExecuteEvent";
import ReviewEvent from "./events/ReviewEvent";
import Comment from "./events/Comment";

export default function ActivityTimeline({
  request,
  websocketEvents = [],
  sendReview,
  closeRequest,
}: {
  request: ExecutionRequestResponseWithComments;
  websocketEvents?: Execute[];
  sendReview?: (comment: string, type: ReviewTypes) => Promise<boolean>;
  closeRequest?: (comment: string) => Promise<boolean>;
}) {
  const historicalEvents = request?.events ? [...request.events] : [];

  // Websocket events carry live results, so they supersede their
  // historical counterparts.
  const wsEventIds = new Set(websocketEvents.map((e) => e.id));
  const events = [
    ...historicalEvents.filter(
      (event) => !(event._type === "EXECUTE" && wsEventIds.has(event.id)),
    ),
    ...websocketEvents,
  ];

  // Newest first, matching the live session activity log
  events.sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );

  const connectionType: DatabaseType | undefined =
    request._type === "DATASOURCE" ? request.connection.type : undefined;

  return (
    <>
      <div className="mt-6">
        <span>Activity</span>
      </div>
      <div>
        {sendReview && (
          <CommentBox
            sendReview={sendReview}
            closeRequest={closeRequest}
            userId={request?.author?.id}
          ></CommentBox>
        )}
        {events.map((event, index) => {
          // the first event connects up to the comment composer when present
          const connectTop = index > 0 || sendReview !== undefined;
          if (event?._type === "EDIT")
            return (
              <EditEvent key={event.id} event={event} connectTop={connectTop} />
            );
          if (event?._type === "EXECUTE")
            return (
              <ExecuteEvent
                key={event.id}
                event={event}
                connectTop={connectTop}
                connectionType={connectionType}
              />
            );
          if (event?._type === "COMMENT")
            return (
              <Comment key={event.id} event={event} connectTop={connectTop} />
            );
          if (event?._type === "REVIEW")
            return (
              <ReviewEvent
                key={event.id}
                event={event}
                connectTop={connectTop}
              />
            );
          return null;
        })}
      </div>
    </>
  );
}
