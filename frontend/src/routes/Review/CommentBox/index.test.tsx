import { vi } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import { UserStatusContext } from "../../../components/UserStatusProvider";
import { ReviewTypes } from "../../../hooks/request";
import CommentBox from "./index";

const REVIEWER_ID = "reviewer-1";
const AUTHOR_ID = "author-1";

const renderCommentBox = (
  props: Partial<Parameters<typeof CommentBox>[0]> = {},
) => {
  const sendReview = vi.fn().mockResolvedValue(true);
  const closeRequest = vi.fn().mockResolvedValue(true);
  render(
    <UserStatusContext.Provider
      value={{
        userStatus: {
          id: REVIEWER_ID,
          email: "reviewer@example.com",
          fullName: "Rev Iewer",
          status: "ok",
        },
        refreshState: async () => {},
      }}
    >
      <CommentBox
        sendReview={sendReview}
        closeRequest={closeRequest}
        userId={AUTHOR_ID}
        {...props}
      />
    </UserStatusContext.Provider>,
  );
  return { sendReview, closeRequest };
};

describe("CommentBox review actions", () => {
  test("shows the review actions without having to open the comment box", () => {
    renderCommentBox();

    for (const action of ["Comment", "Approve", "Request Changes", "Reject"]) {
      const pill = screen.getByTestId(`review-type-${action}`);
      expect(pill).toBeVisible();
      expect(pill).toHaveAttribute("aria-disabled", "false");
    }
    expect(screen.getByTestId("submit-review-button")).toBeVisible();
  });

  test("approves with one click on the action and one on submit", async () => {
    const { sendReview } = renderCommentBox();

    fireEvent.click(screen.getByTestId("review-type-Approve"));
    expect(screen.getByTestId("review-type-Approve")).toHaveAttribute(
      "aria-checked",
      "true",
    );
    expect(screen.getByTestId("submit-review-button")).toHaveTextContent(
      "Approve",
    );

    fireEvent.click(screen.getByTestId("submit-review-button"));

    await waitFor(() =>
      expect(sendReview).toHaveBeenCalledWith("", ReviewTypes.Approve),
    );
  });

  test("sends the typed comment along with the review", async () => {
    const { sendReview } = renderCommentBox();

    fireEvent.change(screen.getByTestId("expand-comment-box"), {
      target: { value: "looks fine to me" },
    });
    fireEvent.click(screen.getByTestId("review-type-Approve"));
    fireEvent.click(screen.getByTestId("submit-review-button"));

    await waitFor(() =>
      expect(sendReview).toHaveBeenCalledWith(
        "looks fine to me",
        ReviewTypes.Approve,
      ),
    );
  });

  test("submits the selected action with the keyboard shortcut", async () => {
    const { sendReview } = renderCommentBox();

    fireEvent.click(screen.getByTestId("review-type-Approve"));
    fireEvent.keyDown(screen.getByTestId("expand-comment-box"), {
      key: "Enter",
      metaKey: true,
    });

    await waitFor(() =>
      expect(sendReview).toHaveBeenCalledWith("", ReviewTypes.Approve),
    );
  });

  test("will not post an empty comment", () => {
    const { sendReview } = renderCommentBox();

    fireEvent.click(screen.getByTestId("submit-review-button"));

    expect(sendReview).not.toHaveBeenCalled();
  });

  test("keeps review actions visible but blocked on your own request", () => {
    renderCommentBox({ userId: REVIEWER_ID });

    const approve = screen.getByTestId("review-type-Approve");
    expect(approve).toBeVisible();
    expect(approve).toHaveAttribute("aria-disabled", "true");
    expect(approve).toHaveAttribute(
      "title",
      "You cannot review your own request",
    );

    fireEvent.click(approve);
    expect(approve).toHaveAttribute("aria-checked", "false");
    expect(screen.getByTestId("submit-review-button")).toHaveTextContent(
      "Comment",
    );
  });

  test("keeps review actions visible but blocked on a rejected request", () => {
    renderCommentBox({ isRejected: true });

    const reject = screen.getByTestId("review-type-Reject");
    expect(reject).toBeVisible();
    expect(reject).toHaveAttribute("aria-disabled", "true");
    expect(reject).toHaveAttribute("title", "This request has been rejected");
  });

  test("does not offer Close to a reviewer", () => {
    renderCommentBox();
    expect(screen.queryByTestId("review-type-Close")).not.toBeInTheDocument();
  });

  test("offers Close to the author", () => {
    renderCommentBox({ userId: REVIEWER_ID });
    expect(screen.getByTestId("review-type-Close")).toBeVisible();
  });

  test("exposes the actions as a radio group driven by the arrow keys", () => {
    renderCommentBox();

    expect(
      screen.getByRole("radiogroup", { name: "Review action" }),
    ).toBeVisible();
    const comment = screen.getByTestId("review-type-Comment");
    expect(comment).toHaveAttribute("aria-checked", "true");

    fireEvent.keyDown(comment, { key: "ArrowRight" });
    expect(screen.getByTestId("review-type-Approve")).toHaveAttribute(
      "aria-checked",
      "true",
    );

    fireEvent.keyDown(screen.getByTestId("review-type-Approve"), {
      key: "ArrowLeft",
    });
    expect(screen.getByTestId("review-type-Comment")).toHaveAttribute(
      "aria-checked",
      "true",
    );
  });

  test("skips blocked actions when arrowing through the group", () => {
    renderCommentBox({ isRejected: true });

    const comment = screen.getByTestId("review-type-Comment");
    expect(comment).toHaveAttribute("aria-checked", "true");

    fireEvent.keyDown(comment, { key: "ArrowRight" });
    expect(comment).toHaveAttribute("aria-checked", "true");
    expect(screen.getByTestId("review-type-Approve")).toHaveAttribute(
      "aria-checked",
      "false",
    );
  });
});
