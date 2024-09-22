import { ReviewTypes } from "../../../hooks/request";

function ReviewRadioBox({
  reviewTypes,
  setChosenReviewType,
  chosenType,
}: {
  reviewTypes: {
    id: ReviewTypes;
    title: string;
    description: string;
    enabled: boolean;
    danger: boolean;
  }[];
  setChosenReviewType: (reviewType: ReviewTypes) => void;
  chosenType: ReviewTypes;
}) {
  return (
    <fieldset>
      <legend className="text-sm font-semibold leading-6 text-slate-900 dark:text-slate-50">
        Review
      </legend>
      <div className="mt-3 space-y-3">
        {reviewTypes.map((reviewType) => (
          <div key={reviewType.id} className="flex items-center">
            <input
              id={reviewType.id}
              name="review-type"
              type="radio"
              defaultChecked={reviewType.id === chosenType}
              className={`h-4 w-4 border-slate-300 focus:ring-indigo-600 disabled:cursor-not-allowed disabled:opacity-50 ${
                reviewType.danger ? "text-red-600" : "text-indigo-600"
              }`}
              onChange={() => setChosenReviewType(reviewType.id)}
              disabled={!reviewType.enabled}
              data-testid={`review-type-${reviewType.title}`}
            />
            <span className="ml-3 block">
              <label
                htmlFor={reviewType.id}
                className={`text-sm font-medium leading-6 ${
                  reviewType.enabled
                    ? reviewType.danger
                      ? "text-red-600 dark:text-red-400"
                      : "text-slate-900 dark:text-slate-50"
                    : "text-slate-400 dark:text-slate-500"
                }`}
              >
                {reviewType.title}
              </label>
              <span>
                <p
                  className={`text-xs ${
                    reviewType.enabled
                      ? reviewType.danger
                        ? "text-red-500 dark:text-red-400"
                        : "text-slate-500 dark:text-slate-400"
                      : "text-slate-400 dark:text-slate-500"
                  }`}
                >
                  {reviewType.description}
                </p>
              </span>
            </span>
          </div>
        ))}
      </div>
    </fieldset>
  );
}

export default ReviewRadioBox;
