import { useContext, useState } from "react";
import ReactMarkdown from "react-markdown";
import Button from "../../../components/Button";
import { UserStatusContext } from "../../../components/UserStatusProvider";
import { AbsoluteInitialBubble as InitialBubble } from "../../../components/InitialBubble";
import { ReviewTypes } from "../../../hooks/request";
import { componentMap } from "../components/Highlighter";
import ReviewRadioBox from "./ReviewRadioBox";

function CommentBox({
  sendReview,
  userId,
}: {
  sendReview: (comment: string, type: ReviewTypes) => Promise<boolean>;
  userId?: string;
}) {
  const [commentFormVisible, setCommentFormVisible] = useState<boolean>(true);
  const [comment, setComment] = useState<string>("");

  const [chosenReviewType, setChosenReviewType] = useState<ReviewTypes>(
    ReviewTypes.Comment,
  );

  const userContext = useContext(UserStatusContext);

  const handleReview = async () => {
    const result = await sendReview(comment, chosenReviewType);
    if (result) {
      setComment("");
    }
  };

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
  ];
  return (
    <div>
      <div className="relative ml-4 py-4">
        <div className="absolute bottom-0 left-0 top-0 block w-0.5 whitespace-pre bg-slate-700">
          {" "}
        </div>
      </div>
      <div className=" relative mb-5 rounded-md shadow-md dark:border dark:border-slate-700">
        <InitialBubble
          name={
            (userContext.userStatus && userContext.userStatus?.fullName) || ""
          }
        />
        <div className="mb-2 rounded-t-md border border-b-slate-300 dark:border-b dark:border-l-0 dark:border-r-0 dark:border-t-0 dark:border-slate-700 dark:bg-slate-900">
          <div className="z-10 -mb-px overflow-auto">
            <button
              className={`ml-2 mt-2 ${
                commentFormVisible
                  ? "border border-b-slate-50 bg-slate-50 dark:border-slate-700 dark:border-b-slate-950 dark:bg-slate-950 dark:text-slate-50"
                  : "dark:hover:bg-slate-800"
              }  rounded-t-md border-slate-300 px-4 py-2 text-sm leading-6 text-slate-600`}
              onClick={() => setCommentFormVisible(true)}
            >
              write
            </button>
            <button
              className={`mt-2 ${
                commentFormVisible
                  ? "dark:hover:bg-slate-800"
                  : "border border-b-white bg-white dark:border-slate-700 dark:border-b-slate-950 dark:bg-slate-950 dark:text-slate-50"
              } rounded-t-md  border-slate-300 px-4 py-2 text-sm leading-6 text-slate-600`}
              onClick={() => setCommentFormVisible(false)}
            >
              preview
            </button>
          </div>
        </div>
        <div className="px-3">
          {commentFormVisible ? (
            <textarea
              className="mb-2 block w-full appearance-none rounded-md border border-slate-100 bg-slate-50 p-1 leading-normal text-gray-700 shadow-sm transition-colors focus:shadow-md focus:outline-none dark:border-slate-700 dark:bg-slate-950 dark:text-slate-50 dark:focus:border-slate-500 dark:hover:border-slate-600 dark:focus:hover:border-slate-500"
              id="comment"
              name="comment"
              rows={4}
              onChange={(event) => setComment(event.target.value)}
              value={comment}
              placeholder="Leave a comment"
            ></textarea>
          ) : (
            <ReactMarkdown
              className="my-2 h-28 max-h-48 overflow-y-scroll border-r-slate-300  scrollbar-thin scrollbar-track-slate-100 scrollbar-thumb-slate-300 scrollbar-track-rounded-md scrollbar-thumb-rounded-md"
              components={componentMap}
            >
              {comment}
            </ReactMarkdown>
          )}
          <div className="p-1">
            <div className="center mb-2 flex flex-col">
              <ReviewRadioBox
                reviewTypes={reviewTypes}
                setChosenReviewType={setChosenReviewType}
                chosenType={chosenReviewType}
              ></ReviewRadioBox>
              <div className="mb-2 flex justify-end">
                <Button
                  id="submit"
                  type="submit"
                  onClick={() => void handleReview()}
                  dataTestId="submit-review-button"
                >
                  Review
                </Button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default CommentBox;
