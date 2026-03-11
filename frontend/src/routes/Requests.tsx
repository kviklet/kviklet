import { ChangeEvent, useEffect, useRef, useState, useCallback } from "react";
import {
  ExecutionRequestResponse,
  getRequestsPaginated,
} from "../api/ExecutionRequestApi";
import { Link } from "react-router-dom";
import Spinner from "../components/Spinner";
import InitialBubble from "../components/InitialBubble";
import {
  CircleStackIcon,
  ClockIcon,
  CloudIcon,
} from "@heroicons/react/20/solid";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "../hooks/useNotification";
import Toggle from "../components/Toggle";
import SearchInput from "../components/SearchInput";
import Tooltip from "../components/Tooltip";

function timeSince(date: Date) {
  const seconds =
    Math.floor((new Date().getTime() - date.getTime()) / 1000) +
    new Date().getTimezoneOffset() * 60;

  const units: [number, string][] = [
    [31536000, "year"],
    [2592000, "month"],
    [86400, "day"],
    [3600, "hour"],
    [60, "minute"],
  ];

  for (const [divisor, unit] of units) {
    const value = Math.floor(seconds / divisor);
    if (value > 0) {
      return `${value} ${unit}${value !== 1 ? "s" : ""} ago`;
    }
  }
  return Math.floor(seconds) + " seconds ago";
}

function mapStatus(reviewStatus: string, executionStatus: string) {
  if (reviewStatus === "AWAITING_APPROVAL" && executionStatus !== "EXECUTED")
    return "Pending";
  else if (executionStatus === "EXECUTED") return "Executed";
  else if (executionStatus === "ACTIVE") return "Active";
  else if (reviewStatus === "CHANGE_REQUESTED") return "Change Requested";
  else if (reviewStatus === "REJECTED") return "Rejected";
  else if (executionStatus === "EXECUTABLE") return "Ready";
  else return "Unknown";
}

function mapStatusToLabelColor(status?: string) {
  switch (status) {
    case "Ready":
      return "dark:ring-lime-400/10 dark:text-lime-500 ring-lime-500/10 text-lime-600 bg-lime-50 dark:bg-lime-400/10";
    case "Pending":
      return "dark:ring-yellow-400/10 dark:text-yellow-500 ring-yellow-500/10 text-yellow-600 bg-yellow-50 dark:bg-yellow-400/10";
    case "Active":
      return "dark:ring-sky-400/10 dark:text-sky-500 ring-sky-500/10 text-sky-600 bg-sky-50 dark:bg-sky-400/10";
    case "Executed":
      return "dark:ring-lime-400/10 dark:text-lime-500 ring-lime-500/10 text-lime-600 bg-lime-50 dark:bg-lime-400/10";
    case "Change Requested":
      return "dark:ring-red-400/10 dark:text-red-500 ring-red-500/10 text-red-600 bg-red-50 dark:bg-red-400/10";
    case "Rejected":
      return "dark:ring-red-400/10 dark:text-red-500 ring-red-500/10 text-red-600 bg-red-50 dark:bg-red-400/10";
    default:
      return "dark:ring-gray-400/10 dark:text-gray-500 ring-gray-500/10 text-gray-600 bg-gray-50 dark:bg-gray-400/10";
  }
}
function mapStatusToBorderColor(status?: string) {
  switch (status) {
    case "Ready":
    case "Executed":
      return "border-l-lime-500 dark:border-l-lime-500";
    case "Pending":
      return "border-l-yellow-500 dark:border-l-yellow-500";
    case "Active":
      return "border-l-sky-500 dark:border-l-sky-500";
    case "Change Requested":
    case "Rejected":
      return "border-l-red-500 dark:border-l-red-500";
    default:
      return "border-l-gray-400 dark:border-l-gray-500";
  }
}

function mapStatusToTextColor(status?: string) {
  switch (status) {
    case "Ready":
    case "Executed":
      return "text-lime-600 dark:text-lime-500";
    case "Pending":
      return "text-yellow-600 dark:text-yellow-500";
    case "Active":
      return "text-sky-600 dark:text-sky-500";
    case "Change Requested":
    case "Rejected":
      return "text-red-600 dark:text-red-500";
    default:
      return "text-gray-500 dark:text-gray-400";
  }
}

function shortTypeLabel(type: string) {
  switch (type) {
    case "SingleExecution":
      return "Query";
    case "TemporaryAccess":
      return "Session";
    case "Dump":
      return "Export";
    default:
      return type;
  }
}

const useRequests = (onlyPending: boolean, searchTerm: string) => {
  const [requests, setRequests] = useState<ExecutionRequestResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(false);
  const [cursor, setCursor] = useState<Date | null>(null);

  const { addNotification } = useNotification();

  const loadRequests = useCallback(
    async (reset: boolean = false) => {
      if (reset) {
        setLoading(true);
        setRequests([]);
        setCursor(null);
      } else {
        setLoadingMore(true);
      }

      const response = await getRequestsPaginated({
        reviewStatuses: onlyPending ? ["AWAITING_APPROVAL"] : undefined,
        executionStatuses: onlyPending ? ["EXECUTABLE", "ACTIVE"] : undefined,
        after: reset ? undefined : cursor ?? undefined,
        limit: 20,
      });

      if (isApiErrorResponse(response)) {
        addNotification({
          title: "Failed to fetch requests",
          text: response.message,
          type: "error",
        });
        setLoading(false);
        setLoadingMore(false);
        return;
      }

      setRequests((prev) =>
        reset ? response.requests : [...prev, ...response.requests],
      );
      setHasMore(response.hasMore);
      setCursor(response.cursor);
      setLoading(false);
      setLoadingMore(false);
    },
    [onlyPending, cursor, addNotification],
  );

  useEffect(() => {
    void loadRequests(true);
  }, [onlyPending]);

  const loadMore = useCallback(() => {
    if (!loadingMore && hasMore) {
      void loadRequests(false);
    }
  }, [loadingMore, hasMore, loadRequests]);

  // Client-side filtering by search term (for multi-field search)
  const filteredRequests = requests.filter(
    (request) =>
      request.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.type.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.reviewStatus.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.executionStatus
        .toLowerCase()
        .includes(searchTerm.toLowerCase()) ||
      request.author.fullName
        ?.toLowerCase()
        .includes(searchTerm.toLowerCase()) ||
      request.connection.displayName
        .toLowerCase()
        .includes(searchTerm.toLowerCase()),
  );

  return {
    requests: filteredRequests,
    loading,
    loadingMore,
    hasMore,
    loadMore,
  };
};

function Requests() {
  const [onlyPending, setOnlyPending] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const { requests, loading, loadingMore, hasMore, loadMore } = useRequests(
    onlyPending,
    searchTerm,
  );
  const observerTarget = useRef<HTMLDivElement>(null);

  // Infinite scroll observer
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting && hasMore && !loadingMore) {
          loadMore();
        }
      },
      { threshold: 0.1 },
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => {
      if (observerTarget.current) {
        observer.unobserve(observerTarget.current);
      }
    };
  }, [hasMore, loadingMore, loadMore]);

  return (
    <div className="h-full">
      <div className="border-b border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-950">
        <div className="mx-auto flex max-w-3xl flex-col gap-3 px-4 py-4 sm:flex-row sm:items-center sm:justify-between">
          <h1 className="text-xl font-medium">Open Requests</h1>
          <div className="flex items-center gap-3">
            <SearchInput
              value={searchTerm}
              onChange={(e: ChangeEvent<HTMLInputElement>) =>
                setSearchTerm(e.target.value)
              }
              placeholder="Search requests..."
              className="w-full sm:w-64"
            />
            <Tooltip position="bottom" content="Show only pending requests">
              <div className="flex shrink-0 items-center">
                <ClockIcon className="mr-2 h-5 w-5 text-slate-400" />
                <Toggle
                  active={onlyPending}
                  onClick={() => setOnlyPending(!onlyPending)}
                />
              </div>
            </Tooltip>
          </div>
        </div>
      </div>
      {(loading && <Spinner size="lg" />) || (
        <div
          className="h-full bg-slate-50 dark:bg-slate-950"
          data-testid="requests-list"
        >
          <div className="mx-auto max-w-3xl px-4 pt-2">
            {requests.length === 0 && (
              <div className="mx-2 my-4 px-4 py-2">
                <h2 className="text-center text-lg">No open requests</h2>
              </div>
            )}
            {requests.map((request) => {
              return (
                <Link
                  key={request.id}
                  to={`/requests/${request.id}`}
                  data-testid={`request-link-${request.title}`}
                >
                  <div
                    className={`my-2 rounded-lg border border-l-4 border-slate-200 bg-white px-4 py-3 shadow-sm transition-colors hover:bg-slate-50 dark:border-slate-700 dark:bg-slate-900 dark:shadow-none dark:hover:bg-slate-800 ${mapStatusToBorderColor(
                      mapStatus(
                        request?.reviewStatus,
                        request?.executionStatus,
                      ),
                    )}`}
                    key={request.id}
                  >
                    <div className="flex items-start gap-3">
                      <InitialBubble
                        name={request.author.fullName || request.author.email}
                        className="h-9 w-9 shrink-0"
                      />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-start justify-between gap-2">
                          <h2 className="truncate text-sm font-medium">
                            {request.title}
                          </h2>
                          <span
                            className="shrink-0 text-xs text-slate-400 dark:text-slate-500"
                            title={
                              new Date(request.createdAt).toLocaleString() +
                              " UTC"
                            }
                          >
                            {timeSince(new Date(request.createdAt))}
                          </span>
                        </div>
                        <p className="mt-0.5 flex flex-wrap items-center gap-1 text-sm text-slate-500 dark:text-slate-400">
                          <span className="truncate font-medium text-slate-600 dark:text-slate-300">
                            {request.author.fullName || request.author.email}
                          </span>
                          <span className="shrink-0">→</span>
                          <span className="inline-flex items-center gap-1 font-medium text-slate-600 dark:text-slate-300">
                            {request._type === "DATASOURCE" ? (
                              <CircleStackIcon className="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
                            ) : (
                              <CloudIcon className="h-3.5 w-3.5 text-slate-400 dark:text-slate-500" />
                            )}
                            {request.connection.displayName}
                          </span>
                        </p>
                        {request.description && (
                          <p className="mt-0.5 line-clamp-1 text-sm text-slate-500 dark:text-slate-400">
                            {request.description}
                          </p>
                        )}
                        <p className="mt-1.5 text-xs text-slate-400 dark:text-slate-500">
                          <span>{shortTypeLabel(request.type)}</span>
                          <span className="mx-1.5">·</span>
                          <span
                            className={mapStatusToTextColor(
                              mapStatus(
                                request?.reviewStatus,
                                request?.executionStatus,
                              ),
                            )}
                          >
                            {mapStatus(
                              request?.reviewStatus,
                              request?.executionStatus,
                            )}
                          </span>
                        </p>
                      </div>
                    </div>
                  </div>
                </Link>
              );
            })}

            {/* Infinite scroll trigger */}
            {hasMore && <div ref={observerTarget} className="h-10" />}

            {/* Loading more indicator */}
            {loadingMore && (
              <div className="my-4 flex justify-center">
                <Spinner size="sm" />
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

export { Requests, mapStatusToLabelColor, mapStatus, timeSince };
