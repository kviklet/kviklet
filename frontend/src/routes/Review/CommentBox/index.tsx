import {
  ChangeEvent,
  KeyboardEvent,
  useContext,
  useRef,
  useState,
} from "react";
import ReactMarkdown from "react-markdown";
import Button from "../../../components/Button";
import { UserStatusContext } from "../../../components/UserStatusProvider";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { ReviewTypes } from "../../../hooks/request";
import { componentMap } from "../components/Highlighter";

type ReviewOption = {
  id: ReviewTypes;
  title: string;
  description: string;
  visible: boolean;
  disabledReason?: string;
  danger: boolean;
};

const isMac =
  typeof navigator !== "undefined" && navigator.userAgent.includes("Mac");
const submitShortcut = isMac ? "⌘↵" : "Ctrl+↵";

function CommentBox({
  sendReview,
  closeRequest,
  userId,
  isRejected,
}: {
  sendReview: (comment: string, type: ReviewTypes) => Promise<boolean>;
  closeRequest?: (comment: string) => Promise<boolean>;
  userId?: string;
  isRejected?: boolean;
}) {
  const [expanded, setExpanded] = useState<boolean>(false);
  const [previewVisible, setPreviewVisible] = useState<boolean>(false);
  const [comment, setComment] = useState<string>("");

  const [chosenReviewType, setChosenReviewType] = useState<ReviewTypes>(
    ReviewTypes.Comment,
  );

  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const radioGroupRef = useRef<HTMLDivElement>(null);

  const userContext = useContext(UserStatusContext);

  const isOwnRequest =
    userContext.userStatus && userContext.userStatus?.id === userId;

  const reviewBlockedReason = isOwnRequest
    ? "You cannot review your own request"
    : isRejected
    ? "This request has been rejected"
    : undefined;

  const reviewTypes: ReviewOption[] = [
    {
      id: ReviewTypes.Comment,
      title: "Comment",
      description: "Submit a general comment without explicit approval",
      visible: true,
      danger: false,
    },
    {
      id: ReviewTypes.Approve,
      title: "Approve",
      description: "Give your approval to execute this request",
      visible: true,
      disabledReason: reviewBlockedReason,
      danger: false,
    },
    {
      id: ReviewTypes.RequestChange,
      title: "Request Changes",
      description:
        "Request a change on this Request, you can later approve it again",
      visible: true,
      disabledReason: reviewBlockedReason,
      danger: true,
    },
    {
      id: ReviewTypes.Reject,
      title: "Reject",
      description: "Reject this request from ever executing",
      visible: true,
      disabledReason: reviewBlockedReason,
      danger: true,
    },
    {
      id: ReviewTypes.Close,
      title: "Close",
      description: "Close this request without executing it",
      visible: !!(isOwnRequest && closeRequest),
      disabledReason: isRejected ? "This request has been rejected" : undefined,
      danger: false,
    },
  ];

  const visibleReviewTypes = reviewTypes.filter(
    (reviewType) => reviewType.visible,
  );
  const selectableReviewTypes = visibleReviewTypes.filter(
    (reviewType) => !reviewType.disabledReason,
  );
  const selectedReviewType =
    selectableReviewTypes.find(
      (reviewType) => reviewType.id === chosenReviewType,
    ) ?? selectableReviewTypes[0];

  // a bare comment with no text would just add an empty event to the timeline
  const submitDisabled =
    selectedReviewType.id === ReviewTypes.Comment && comment.trim() === "";

  const autoGrow = (element: HTMLTextAreaElement) => {
    element.style.height = "auto";
    element.style.height = `${Math.min(element.scrollHeight, 256)}px`;
  };

  const collapse = () => {
    setExpanded(false);
    setPreviewVisible(false);
    if (textareaRef.current) {
      textareaRef.current.style.height = "";
    }
  };

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
      collapse();
    }
  };

  const handleCommentChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    setComment(event.target.value);
    autoGrow(event.target);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === "Escape") {
      if (comment.trim() === "") {
        collapse();
      }
      textareaRef.current?.blur();
    } else if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) {
      void handleReview();
    }
  };

  const selectReviewType = (reviewType: ReviewOption) => {
    if (reviewType.disabledReason) {
      return;
    }
    setChosenReviewType(reviewType.id);
    setExpanded(true);
  };

  const handleReviewTypeClick = (reviewType: ReviewOption) => {
    if (reviewType.disabledReason) {
      return;
    }
    selectReviewType(reviewType);
    textareaRef.current?.focus();
  };

  const handleReviewTypeKeyDown = (event: KeyboardEvent<HTMLButtonElement>) => {
    const forward = event.key === "ArrowRight" || event.key === "ArrowDown";
    const backward = event.key === "ArrowLeft" || event.key === "ArrowUp";
    if (!forward && !backward) {
      return;
    }
    event.preventDefault();
    const current = selectableReviewTypes.findIndex(
      (reviewType) => reviewType.id === selectedReviewType.id,
    );
    const next =
      selectableReviewTypes[
        (current + (forward ? 1 : -1) + selectableReviewTypes.length) %
          selectableReviewTypes.length
      ];
    selectReviewType(next);
    radioGroupRef.current
      ?.querySelector<HTMLButtonElement>(
        `[data-testid="review-type-${next.title}"]`,
      )
      ?.focus();
  };

  const pillClassName = (reviewType: ReviewOption) => {
    if (reviewType.disabledReason) {
      return "cursor-not-allowed border-slate-200 text-slate-400 dark:border-slate-800 dark:text-slate-600";
    }
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

  const placeholder =
    selectedReviewType.id === ReviewTypes.Comment
      ? "Leave a comment"
      : "Add an optional comment";

  return (
    <div className="relative mt-4">
      <InitialBubble
        name={
          (userContext.userStatus && userContext.userStatus?.fullName) || ""
        }
      />
      <div
        className={`rounded-md border bg-white transition-shadow dark:bg-slate-900 dark:shadow-none ${
          expanded
            ? "border-slate-300 shadow-md dark:border-slate-600"
            : "border-slate-200 shadow-sm dark:border-slate-700"
        }`}
      >
        {previewVisible ? (
          <div className="max-h-64 min-h-[5rem] overflow-y-auto px-3 py-2 text-sm">
            {comment.trim() === "" ? (
              <p className="text-slate-400 dark:text-slate-500">
                Nothing to preview
              </p>
            ) : (
              <ReactMarkdown components={componentMap}>{comment}</ReactMarkdown>
            )}
          </div>
        ) : (
          <textarea
            ref={textareaRef}
            id="comment"
            name="comment"
            rows={1}
            aria-label="Comment"
            data-testid="expand-comment-box"
            className={`block max-h-64 w-full resize-none rounded-t-md border-0 bg-transparent px-3 py-2 text-sm leading-normal text-slate-900 placeholder:text-slate-400 focus:outline-none dark:text-slate-50 dark:placeholder:text-slate-500 ${
              expanded ? "min-h-[4.5rem]" : ""
            }`}
            onChange={handleCommentChange}
            onKeyDown={handleKeyDown}
            onFocus={() => setExpanded(true)}
            value={comment}
            placeholder={placeholder}
          ></textarea>
        )}
        <div className="flex flex-wrap items-center gap-1.5 border-t border-slate-200 px-2 py-2 dark:border-slate-700">
          <div
            ref={radioGroupRef}
            role="radiogroup"
            aria-label="Review action"
            className="flex flex-wrap items-center gap-1.5"
          >
            {visibleReviewTypes.map((reviewType) => {
              const selected = reviewType.id === selectedReviewType.id;
              return (
                <button
                  key={reviewType.id}
                  type="button"
                  role="radio"
                  aria-checked={selected}
                  aria-disabled={reviewType.disabledReason !== undefined}
                  tabIndex={selected ? 0 : -1}
                  title={reviewType.disabledReason ?? reviewType.description}
                  data-testid={`review-type-${reviewType.title}`}
                  onClick={() => handleReviewTypeClick(reviewType)}
                  onKeyDown={handleReviewTypeKeyDown}
                  className={`rounded-full border px-2.5 py-0.5 text-xs font-medium transition-colors ${pillClassName(
                    reviewType,
                  )}`}
                >
                  {reviewType.title}
                </button>
              );
            })}
          </div>
          <div className="ml-auto flex items-center gap-2 pl-2">
            {expanded && (
              <span
                aria-hidden="true"
                className="hidden text-xs text-slate-400 dark:text-slate-500 sm:inline"
              >
                {submitShortcut}
              </span>
            )}
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
              title={`${selectedReviewType.title} (${submitShortcut})`}
            >
              {selectedReviewType.title}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}

export default CommentBox;
