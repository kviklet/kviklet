import { ChangeEvent, useEffect, useRef, useState, useCallback } from "react";
import {
  ExecutionRequestResponse,
  getRequestsPaginated,
} from "../api/ExecutionRequestApi";
import { Link } from "react-router-dom";
import Button from "../components/Button";
import Spinner from "../components/Spinner";
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

  let interval = seconds / 31536000;

  if (interval > 1) {
    return Math.floor(interval) + " years ago";
  }
  interval = seconds / 2592000;
  if (interval > 1) {
    return Math.floor(interval) + " months ago";
  }
  interval = seconds / 86400;
  if (interval > 1) {
    return Math.floor(interval) + " days ago";
  }
  interval = seconds / 3600;
  if (interval > 1) {
    return Math.floor(interval) + " hours ago";
  }
  interval = seconds / 60;
  if (interval > 1) {
    return Math.floor(interval) + " minutes ago";
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

  // Client-side filtering by search term (kept for multi-field search)
  const filteredRequests = requests.filter(
    (request) =>
      request.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.description.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.type.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.reviewStatus.toLowerCase().includes(searchTerm.toLowerCase()) ||
      request.executionStatus
        .toLowerCase()
        .includes(searchTerm.toLowerCase()) ||
      request.author.fullName?.toLowerCase().includes(searchTerm.toLowerCase()),
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
      <div className=" border-b border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-950">
        <h1 className=" m-5 mx-auto max-w-5xl pl-1.5 text-xl">
          {" "}
          Open Requests
        </h1>
      </div>
      {(loading && <Spinner></Spinner>) || (
        <div
          className="h-full bg-slate-50 dark:bg-slate-950"
          data-testid="requests-list"
        >
          <div className="mx-auto max-w-5xl ">
            <div className="mb-2 mt-4 flex flex-row items-center justify-between">
              <SearchInput
                value={searchTerm}
                onChange={(e: ChangeEvent<HTMLInputElement>) =>
                  setSearchTerm(e.target.value)
                }
                placeholder="Search requests..."
                className="w-full"
              />
              <Tooltip position="bottom" content="Show only pending requests">
                <div className="ml-4 flex items-center">
                  <ClockIcon className="mr-2 h-5 w-5 text-slate-400" />
                  <Toggle
                    active={onlyPending}
                    onClick={() => setOnlyPending(!onlyPending)}
                  />
                </div>
              </Tooltip>
            </div>

            {requests.length === 0 && (
              <div className="mx-2 my-4 px-4 py-2">
                <h2 className="text-center text-lg">No open requests</h2>
              </div>
            )}
            {requests.map((request) => {
              return (
                <Link
                  to={`/requests/${request.id}`}
                  data-testid={`request-link-${request.title}`}
                >
                  <div
                    className="my-4 rounded-md border border-slate-200 bg-white px-4 py-4 shadow-md transition-colors hover:bg-slate-50 dark:border dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800"
                    key={request.id}
                  >
                    <div className="flex">
                      <div className="mb-2 flex flex-col">
                        <h2 className="text-md">{request.title}</h2>
                        <p className="text-slate-600 dark:text-slate-400">
                          {request.description}
                        </p>
                        {(request._type === "DATASOURCE" && (
                          <CircleStackIcon className="mt-auto w-4 text-slate-400 dark:text-slate-600"></CircleStackIcon>
                        )) || (
                          <CloudIcon className="mt-auto w-4 text-slate-400 dark:text-slate-600"></CloudIcon>
                        )}
                      </div>
                      <div className="ml-auto flex flex-col items-end">
                        <div
                          className="mb-2 text-sm text-slate-600 dark:text-slate-400"
                          title={
                            new Date(request.createdAt).toLocaleString() +
                            " UTC"
                          }
                        >
                          {timeSince(new Date(request.createdAt))}
                        </div>
                        <span
                          className={`${mapStatusToLabelColor(
                            mapStatus(
                              request?.reviewStatus,
                              request?.executionStatus,
                            ),
                          )} mt-2 rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset`}
                        >
                          {mapStatus(
                            request?.reviewStatus,
                            request?.executionStatus,
                          )}
                        </span>
                        <span
                          className={`mt-2 w-min rounded-md bg-cyan-50 px-2 py-1 text-xs  font-medium text-cyan-600 ring-1 ring-inset ring-cyan-500/10 dark:bg-cyan-400/10 dark:text-cyan-500 dark:ring-cyan-400/20`}
                        >
                          {request.type}
                        </span>
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
                <Spinner />
              </div>
            )}

            {/* Create new request button */}
            <div className="my-6">
              <Link to={"/new"}>
                <Button className="float-right">Create new Request</Button>
              </Link>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export { Requests, mapStatusToLabelColor, mapStatus, timeSince };
