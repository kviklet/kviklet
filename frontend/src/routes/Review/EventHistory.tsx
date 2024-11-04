import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import { ReviewTypes } from "../../hooks/request";
import CommentBox from "./CommentBox";
import EditEvent from "./events/EditEvent";
import ExecuteEvent from "./events/ExecuteEvent";
import ReviewEvent from "./events/ReviewEvent";
import Comment from "./events/Comment";

export default function EventHistory({
  request,
  sendReview,
  reverse,
}: {
  request: ExecutionRequestResponseWithComments;
  sendReview?: (comment: string, type: ReviewTypes) => Promise<boolean>;
  reverse?: boolean;
}) {
  const events = request?.events ? [...request.events] : [];
  if (reverse) {
    events.reverse();
  }

  return (
    <>
      <div className="mt-6">
        <span>Activity</span>
      </div>
      <div>
        {events.map((event, index) => {
          if (event?._type === "EDIT")
            return <EditEvent event={event} index={index}></EditEvent>;
          if (event?._type === "EXECUTE")
            return <ExecuteEvent event={event} index={index}></ExecuteEvent>;
          if (event?._type === "COMMENT")
            return <Comment event={event} index={index}></Comment>;
          if (event?._type === "REVIEW")
            return <ReviewEvent event={event} index={index}></ReviewEvent>;
        })}

        {sendReview && (
          <CommentBox
            sendReview={sendReview}
            userId={request?.author?.id}
          ></CommentBox>
        )}
      </div>
    </>
  );
}
