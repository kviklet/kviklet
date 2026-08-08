import { ReactNode, useContext, useState } from "react";
import { CheckIcon } from "@heroicons/react/20/solid";
import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import { mapStatus, mapStatusToLabelColor, timeSince } from "../Requests";
import { ReviewTypes, latestReviewAction } from "../../hooks/request";
import { hasPermission } from "../../api/Permissions";
import { UserStatusContext } from "../../components/UserStatusProvider";
import Button from "../../components/Button";
import InitialBubble from "../../components/InitialBubble";
import ConnectionLink from "./components/ConnectionLink";
import ApprovalProgress from "./ApprovalProgress";

const requestTypeLabel = (
  request: ExecutionRequestResponseWithComments,
): string => {
  if (request.type === "TemporaryAccess") {
    return "Temporary Access";
  }
  if (request._type === "KUBERNETES") {
    return "Command";
  }
  return request.type === "Dump" ? "SQL Dump" : "Query";
};

function SidebarSection({
  label,
  children,
}: {
  label: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1">
      <div className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
        {label}
      </div>
      <div className="text-sm text-slate-800 dark:text-slate-200">
        {children}
      </div>
    </div>
  );
}

function SidebarDivider() {
  return <div className="border-b border-slate-200 dark:border-slate-700" />;
}

// GitHub-PR-style sidebar: status and actions on top (always reachable without
// scrolling past long statements), then the request metadata, then approvals.
// The action buttons differ per request type, so they come in as children.
function RequestSidebar({
  request,
  sendReview,
  children,
}: {
  request: ExecutionRequestResponseWithComments;
  sendReview?: (comment: string, type: ReviewTypes) => Promise<boolean>;
  children: ReactNode;
}) {
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const userContext = useContext(UserStatusContext);
  const progress = request.approvalProgress;
  const showApprovals =
    !!progress &&
    (progress.totalRequired > 0 || progress.roleProgress.length > 0);

  const currentUserId = userContext.userStatus
    ? userContext.userStatus.id
    : undefined;
  const isOwnRequest = currentUserId === request.author.id;
  const canReview = hasPermission(
    request.permissions,
    "execution_request:review",
  );
  const hasApproved = latestReviewAction(request, currentUserId) === "APPROVE";
  // No disabled states here: users who can never approve (authors, missing
  // permission) see nothing, a stale approval after an edit re-shows the button.
  const showApproveButton =
    !!sendReview &&
    canReview &&
    !isOwnRequest &&
    !hasApproved &&
    (request.reviewStatus === "AWAITING_APPROVAL" ||
      request.reviewStatus === "CHANGE_REQUESTED") &&
    request.executionStatus !== "EXECUTED";
  const showYouApproved =
    hasApproved && !isOwnRequest && request.reviewStatus !== "REJECTED";

  const handleApprove = async () => {
    if (!sendReview || reviewSubmitting) {
      return;
    }
    setReviewSubmitting(true);
    try {
      await sendReview("", ReviewTypes.Approve);
    } finally {
      setReviewSubmitting(false);
    }
  };

  return (
    <aside className="flex w-full flex-col gap-4 border-slate-200 dark:border-slate-700 md:order-last md:w-60 md:shrink-0 md:border-l md:pl-4">
      <div
        className={`${mapStatusToLabelColor(
          mapStatus(request.reviewStatus, request.executionStatus),
        )} w-fit rounded-md px-2 py-1 text-sm font-medium ring-1 ring-inset`}
      >
        {mapStatus(request.reviewStatus, request.executionStatus)}
      </div>
      {children}
      <SidebarDivider />
      <div className="grid grid-cols-2 gap-4 md:flex md:flex-col">
        <SidebarSection label="Requester">
          <div className="flex items-center gap-2">
            <InitialBubble name={request.author.fullName} />
            <span className="min-w-0 truncate">{request.author.fullName}</span>
          </div>
        </SidebarSection>
        <SidebarSection label="Connection">
          <ConnectionLink
            connectionId={request.connection.id}
            displayName={request.connection.displayName}
          />
        </SidebarSection>
        <SidebarSection label="Type">
          {requestTypeLabel(request)}
          {request.type === "TemporaryAccess" && (
            <div className="text-slate-500 dark:text-slate-400">
              {request.temporaryAccessDuration != null
                ? `Valid for ${request.temporaryAccessDuration} minutes`
                : "Valid indefinitely"}
            </div>
          )}
        </SidebarSection>
        <SidebarSection label="Created">
          <span title={request.createdAt.toLocaleString()}>
            {timeSince(request.createdAt)}
          </span>
        </SidebarSection>
      </div>
      {(showApprovals || showApproveButton || showYouApproved) && (
        <>
          <SidebarDivider />
          <SidebarSection label="Approvals">
            <div className="flex flex-col gap-3">
              <ApprovalProgress request={request} />
              {showApproveButton && (
                <Button
                  className="w-full"
                  variant={reviewSubmitting ? "disabled" : "success"}
                  onClick={() => void handleApprove()}
                  dataTestId="sidebar-approve-button"
                >
                  Approve
                </Button>
              )}
              {showYouApproved && (
                <div
                  className="flex items-center gap-1 text-sm text-green-600 dark:text-green-400"
                  data-testid="sidebar-you-approved"
                >
                  <CheckIcon className="h-4 w-4 shrink-0" />
                  You approved
                </div>
              )}
            </div>
          </SidebarSection>
        </>
      )}
    </aside>
  );
}

export default RequestSidebar;
