import { ReactNode } from "react";
import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import { mapStatus, mapStatusToLabelColor, timeSince } from "../Requests";
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
  children,
}: {
  request: ExecutionRequestResponseWithComments;
  children: ReactNode;
}) {
  const progress = request.approvalProgress;
  const showApprovals =
    !!progress &&
    (progress.totalRequired > 0 || progress.roleProgress.length > 0);

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
      {showApprovals && (
        <>
          <SidebarDivider />
          <SidebarSection label="Approvals">
            <ApprovalProgress request={request} />
          </SidebarSection>
        </>
      )}
    </aside>
  );
}

export default RequestSidebar;
