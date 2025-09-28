import { ExecutionRequestResponseWithComments, Execute } from "../api/ExecutionRequestApi";
import EditEvent from "./Review/events/EditEvent";
import ExecuteEvent from "./Review/events/ExecuteEvent";
import ReviewEvent from "./Review/events/ReviewEvent";
import Comment from "./Review/events/Comment";

export default function LiveSessionActivityLog({
  request,
  websocketEvents,
}: {
  request: ExecutionRequestResponseWithComments;
  websocketEvents: Execute[];
}) {
  // Combine historical events with websocket events
  const historicalEvents = request?.events ? [...request.events] : [];

  // Create a map of websocket events by ID for deduplication
  const wsEventIds = new Set(websocketEvents.map(e => e.id));

  // Filter out historical execute events that have been updated via websocket
  const filteredHistoricalEvents = historicalEvents.filter(event => {
    if (event._type === "EXECUTE" && wsEventIds.has(event.id)) {
      return false; // Skip this event as we have a websocket version with results
    }
    return true;
  });

  // Combine filtered historical events with websocket events
  const allEvents = [...filteredHistoricalEvents, ...websocketEvents];

  // Sort by createdAt in reverse chronological order (newest first)
  allEvents.sort((a, b) => {
    const dateA = new Date(a.createdAt).getTime();
    const dateB = new Date(b.createdAt).getTime();
    return dateB - dateA; // Reverse order
  });

  return (
    <>
      <div className="mt-6">
        <span>Activity</span>
      </div>
      <div>
        {allEvents.map((event, index) => {
          if (event?._type === "EDIT")
            return <EditEvent key={event.id} event={event} index={index} />;
          if (event?._type === "EXECUTE")
            return <ExecuteEvent key={event.id} event={event} index={index} />;
          if (event?._type === "COMMENT")
            return <Comment key={event.id} event={event} index={index} />;
          if (event?._type === "REVIEW")
            return <ReviewEvent key={event.id} event={event} index={index} />;
          return null;
        })}
      </div>
    </>
  );
}