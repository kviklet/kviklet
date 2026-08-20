import { useContext, useEffect, useState } from "react";
import { UserStatusContext } from "../components/UserStatusProvider";
import {
  OncallGrantResponse,
  UserResponse,
  approveOncallGrant,
  fetchUsers,
  revokeOncallGrant,
} from "../api/UserApi";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "../hooks/useNotification";
import Button from "../components/Button";
import InitialBubble from "../components/InitialBubble";

function formatDuration(minutes?: number): string {
  if (!minutes) {
    return "";
  }
  if (minutes % 1440 === 0) {
    return `${minutes / 1440}d`;
  }
  if (minutes % 60 === 0) {
    return `${minutes / 60}h`;
  }
  return `${minutes}m`;
}

function grantTitle(grant: OncallGrantResponse): string {
  return grant.kind === "OUTAGE" ? "Outage access" : "On-call access";
}

function OncallPendingRequests() {
  const { userStatus, refreshState } = useContext(UserStatusContext);
  const { addNotification } = useNotification();
  const [pendingUsers, setPendingUsers] = useState<UserResponse[]>([]);
  const [actingId, setActingId] = useState<string | null>(null);

  const canManageOncall =
    userStatus && userStatus !== false
      ? Boolean(userStatus.canManageOncall)
      : false;

  const reload = async () => {
    const response = await fetchUsers();
    if (isApiErrorResponse(response)) {
      return;
    }
    setPendingUsers(
      response.users.filter((user) => user.pendingOncallGrant != null),
    );
  };

  useEffect(() => {
    if (!userStatus) {
      return;
    }
    void reload();
  }, [userStatus && userStatus !== false ? userStatus.id : undefined]);

  const ownPending =
    userStatus && userStatus !== false
      ? userStatus.pendingOncallGrant
      : undefined;

  if (!userStatus) {
    return null;
  }

  let visible = canManageOncall
    ? pendingUsers
    : pendingUsers.filter((user) => user.id === userStatus.id);

  if (
    visible.length === 0 &&
    ownPending &&
    userStatus !== false
  ) {
    visible = [
      {
        id: userStatus.id,
        email: userStatus.email,
        fullName: userStatus.fullName ?? null,
        roles: [],
        pendingOncallGrant: ownPending,
      },
    ];
  }

  if (visible.length === 0) {
    return null;
  }

  const act = async (userId: string, approve: boolean) => {
    setActingId(userId);
    const response = approve
      ? await approveOncallGrant(userId)
      : await revokeOncallGrant(userId);
    setActingId(null);
    if (approve ? isApiErrorResponse(response) : response !== null) {
      addNotification({
        title: approve ? "Failed to approve" : "Failed to deny",
        text:
          approve && isApiErrorResponse(response)
            ? response.message
            : response && "message" in response
              ? response.message
              : "Request failed",
        type: "error",
      });
      return;
    }
    addNotification({
      title: approve ? "On-call request approved" : "On-call request denied",
      text: approve
        ? "Access to every connection is now active."
        : "The request was revoked.",
      type: "info",
    });
    await reload();
    await refreshState();
  };

  return (
    <div className="mb-4" data-testid="oncall-pending-list">
      <h2 className="mb-2 text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
        On-call / outage
      </h2>
      {visible.map((user) => {
        const grant = user.pendingOncallGrant!;
        const duration = formatDuration(grant.durationMinutes);
        return (
          <div
            key={grant.id}
            className="my-2 rounded-lg border border-l-4 border-slate-200 border-l-yellow-500 bg-white px-4 py-3 shadow-sm dark:border-slate-700 dark:border-l-yellow-500 dark:bg-slate-900 dark:shadow-none"
            data-testid={`oncall-pending-card-${user.email}`}
          >
            <div className="flex items-start gap-3">
              <InitialBubble
                name={user.fullName || user.email}
                className="h-9 w-9 shrink-0"
              />
              <div className="min-w-0 flex-1">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="truncate text-sm font-medium">
                    {grantTitle(grant)}
                    {duration ? ` · ${duration}` : ""}
                  </h2>
                  <span className="shrink-0 text-xs font-medium text-yellow-600 dark:text-yellow-500">
                    Pending
                  </span>
                </div>
                <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
                  <span className="font-medium text-slate-600 dark:text-slate-300">
                    {user.fullName || user.email}
                  </span>
                  <span> → all connections</span>
                </p>
                {grant.reason && (
                  <p className="mt-0.5 line-clamp-2 text-sm text-slate-500 dark:text-slate-400">
                    {grant.reason}
                  </p>
                )}
                {canManageOncall && (
                  <div className="mt-2 flex gap-2">
                    <Button
                      size="sm"
                      variant="success"
                      onClick={() => void act(user.id, true)}
                      dataTestId={`approve-oncall-${user.email}`}
                    >
                      {actingId === user.id ? "Working..." : "Approve"}
                    </Button>
                    <Button
                      size="sm"
                      variant="danger"
                      onClick={() => void act(user.id, false)}
                      dataTestId={`deny-oncall-${user.email}`}
                    >
                      Deny
                    </Button>
                  </div>
                )}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export default OncallPendingRequests;
