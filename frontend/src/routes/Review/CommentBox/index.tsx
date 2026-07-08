import { ChangeEvent, KeyboardEvent, useContext, useState } from "react";
import ReactMarkdown from "react-markdown";
import Button from "../../../components/Button";
import { UserStatusContext } from "../../../components/UserStatusProvider";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { ReviewTypes } from "../../../hooks/request";
import { componentMap } from "../components/Highlighter";

function CommentBox({
  sendReview,
  closeRequest,
  userId,
}: {
  sendReview: (comment: string, type: ReviewTypes) => Promise<boolean>;
  closeRequest?: (comment: string) => Promise<boolean>;
  userId?: string;
}) {
  const [expanded, setExpanded] = useState<boolean>(false);
  const [previewVisible, setPreviewVisible] = useState<boolean>(false);
  const [comment, setComment] = useState<string>("");

  const [chosenReviewType, setChosenReviewType] = useState<ReviewTypes>(
    ReviewTypes.Comment,
  );

  const userContext = useContext(UserStatusContext);

  const isOwnRequest =
    userContext.userStatus && userContext.userStatus?.id === userId;

  const reviewTypes = [
    {
      id: ReviewTypes.Comment,
      title: "Comment",
      description: "Submit a general comment without explicit approval",
      enabled: true,
      danger: false,
    },
    {
      id: ReviewTypes.Approve,
      title: "Approve",
      description: "Give your approval to execute this request",
      enabled: !isOwnRequest,
      danger: false,
    },
    {
      id: ReviewTypes.RequestChange,
      title: "Request Changes",
      description:
        "Request a change on this Request, you can later approve it again",
      enabled: !isOwnRequest,
      danger: true,
    },
    {
      id: ReviewTypes.Reject,
      title: "Reject",
      description: "Reject this request from ever executing",
      enabled: !isOwnRequest,
      danger: true,
    },
    {
      id: ReviewTypes.Close,
      title: "Close",
      description: "Close this request without executing it",
      enabled: !!(isOwnRequest && closeRequest),
      danger: false,
    },
  ];

  const availableReviewTypes = reviewTypes.filter(
    (reviewType) => reviewType.enabled,
  );
  const selectedReviewType =
    availableReviewTypes.find(
      (reviewType) => reviewType.id === chosenReviewType,
    ) ?? availableReviewTypes[0];

  // a bare comment with no text would just add an empty event to the timeline
  const submitDisabled =
    selectedReviewType.id === ReviewTypes.Comment && comment.trim() === "";

  const handleReview = async () => {
    if (submitDisabled) {
      return;
    }
    let result: boolean;
    if (selectedReviewType.id === ReviewTypes.Close && closeRequest) {
      result = await closeRequest(comment);
    } else {
      result = await sendReview(comment, selectedReviewType.id);
    }
    if (result) {
      setComment("");
      setChosenReviewType(ReviewTypes.Comment);
      setPreviewVisible(false);
      setExpanded(false);
    }
  };

  const handleCommentChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    setComment(event.target.value);
    event.target.style.height = "auto";
    event.target.style.height = `${Math.min(event.target.scrollHeight, 256)}px`;
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Escape") {
      setExpanded(false);
    } else if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      void handleReview();
    }
  };

  const pillClassName = (reviewType: (typeof reviewTypes)[number]) => {
    const selected = reviewType.id === selectedReviewType.id;
    if (selected) {
      return reviewType.danger
        ? "border-red-600 bg-red-50 text-red-700 dark:border-red-500/60 dark:bg-red-500/20 dark:text-red-300"
        : "border-indigo-600 bg-indigo-50 text-indigo-700 dark:border-indigo-500/60 dark:bg-indigo-500/20 dark:text-indigo-300";
    }
    return reviewType.danger
      ? "border-slate-200 text-red-600/80 hover:border-red-300 dark:border-slate-700 dark:text-red-400/80 dark:hover:border-red-500/50"
      : "border-slate-200 text-slate-600 hover:border-slate-300 dark:border-slate-700 dark:text-slate-400 dark:hover:border-slate-500";
  };

  return (
    <div className="relative mt-4">
      <InitialBubble
        name={
          (userContext.userStatus && userContext.userStatus?.fullName) || ""
        }
      />
      {expanded ? (
        <div className="rounded-md border bg-white shadow-md dark:border-slate-700 dark:bg-slate-900 dark:shadow-none">
          {previewVisible ? (
            <div className="max-h-64 min-h-[5rem] overflow-y-auto px-3 py-2 text-sm">
              {comment.trim() === "" ? (
                <p className="text-slate-400 dark:text-slate-500">
                  Nothing to preview
                </p>
              ) : (
                <ReactMarkdown components={componentMap}>
                  {comment}
                </ReactMarkdown>
              )}
            </div>
          ) : (
            <textarea
              autoFocus
              id="comment"
              name="comment"
              rows={3}
              className="block max-h-64 w-full resize-none rounded-t-md border-0 bg-transparent px-3 py-2 text-sm leading-normal text-slate-900 placeholder:text-slate-400 focus:outline-none dark:text-slate-50 dark:placeholder:text-slate-500"
              onChange={handleCommentChange}
              onKeyDown={handleKeyDown}
              value={comment}
              placeholder="Leave a comment"
            ></textarea>
          )}
          <div className="flex flex-wrap items-center gap-1.5 border-t border-slate-200 px-2 py-2 dark:border-slate-700">
            {availableReviewTypes.map((reviewType) => (
              <button
                key={reviewType.id}
                type="button"
                title={reviewType.description}
                data-testid={`review-type-${reviewType.title}`}
                onClick={() => setChosenReviewType(reviewType.id)}
                className={`rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors ${pillClassName(
                  reviewType,
                )}`}
              >
                {reviewType.title}
              </button>
            ))}
            <div className="ml-auto flex items-center gap-2 pl-2">
              <button
                type="button"
                title="Markdown supported"
                className="text-xs text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
                onClick={() => setPreviewVisible(!previewVisible)}
              >
                {previewVisible ? "Write" : "Preview"}
              </button>
              <Button
                id="submit"
                variant={
                  submitDisabled
                    ? "disabled"
                    : selectedReviewType.danger
                    ? "danger"
                    : "primary"
                }
                onClick={() => void handleReview()}
                dataTestId="submit-review-button"
              >
                {selectedReviewType.title}
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <button
          type="button"
          data-testid="expand-comment-box"
          className="w-full rounded-md border border-slate-200 bg-slate-50 px-3 py-2 text-left text-sm text-slate-500 shadow-sm transition-colors hover:border-slate-300 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-400 dark:hover:border-slate-600"
          onClick={() => setExpanded(true)}
        >
          {isOwnRequest ? "Leave a comment…" : "Leave a comment or review…"}
        </button>
      )}
    </div>
  );
}

export default CommentBox;
