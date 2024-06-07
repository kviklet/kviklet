import { useEffect, useState } from "react";
import {
  ExecutionRequestResponse,
  getRequests,
} from "../api/ExecutionRequestApi";
import { Link } from "react-router-dom";
import Button from "../components/Button";
import Spinner from "../components/Spinner";
import { CircleStackIcon, CloudIcon } from "@heroicons/react/20/solid";
import { isApiErrorResponse } from "../api/Errors";
import useNotification from "../hooks/useNotification";

const Toggle = (props: { active: boolean; onClick: () => void }) => {
  return (
    <label
      className="relative z-0 inline-flex cursor-pointer items-center"
      onClick={props.onClick}
    >
      <input
        type="checkbox"
        checked={props.active}
        className="peer sr-only"
        readOnly
        onClick={(event) => event.stopPropagation()}
      />
      <div
        className="peer h-6 w-11 rounded-full bg-slate-200
      after:absolute after:left-[2px] after:top-0.5 after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-[''] 
      peer-checked:bg-indigo-800 peer-checked:after:translate-x-full peer-checked:after:border-white 
      dark:border-gray-600 dark:bg-slate-700"
      ></div>
    </label>
  );
};

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
  else if (executionStatus === "EXECUTABLE") return "Ready";
  else if (executionStatus === "ACTIVE") return "Active";
  else if (executionStatus === "EXECUTED") return "Executed";
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
    default:
      return "dark:ring-gray-400/10 dark:text-gray-500 ring-gray-500/10 text-gray-600 bg-gray-50 dark:bg-gray-400/10";
  }
}
const useRequests = (onlyPending: boolean) => {
  const [requests, setRequests] = useState<ExecutionRequestResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  const { addNotification } = useNotification();

  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      const requests = await getRequests();
      if (isApiErrorResponse(requests)) {
        addNotification({
          title: "Failed to fetch requests",
          text: requests.message,
          type: "info",
        });
        addNotification({
          title: "Failed to fetch requests",
          text: requests.message,
          type: "error",
        });
        setLoading(false);
        return;
      }
      setRequests(requests);
      setLoading(false);
    };
    void fetchData();
  }, []);

  const visibleRequests = onlyPending
    ? requests.filter((r) => r.reviewStatus === "AWAITING_APPROVAL")
    : requests;

  const sortedRequests = [...visibleRequests].sort((a, b) => {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  return { requests: sortedRequests, loading };
};

function Requests() {
  const [onlyPending, setOnlyPending] = useState(false);
  const { requests, loading } = useRequests(onlyPending);

  return (
    <div className="h-full">
      <div className=" border-b border-slate-300 bg-slate-50 dark:border-slate-700 dark:bg-slate-950">
        <h1 className=" m-5 mx-auto max-w-5xl pl-1.5 text-xl">
          {" "}
          Open Requests
        </h1>
      </div>
      {(loading && <Spinner></Spinner>) || (
        <div className="h-full bg-slate-50 dark:bg-slate-950">
          <div className="mx-auto max-w-5xl ">
            <div className="flex flex-row">
              <div className="mb-2 ml-auto mr-2 mt-4 flex flex-row">
                <label className="mr-2">Only show pending requests</label>
                <Toggle
                  active={onlyPending}
                  onClick={() => setOnlyPending(!onlyPending)}
                ></Toggle>
              </div>
            </div>

            {requests.length === 0 && (
              <div className="mx-2 my-4 px-4 py-2">
                <h2 className="text-center text-lg">No open requests</h2>
              </div>
            )}
            {requests.map((request) => {
              return (
                <Link to={`/requests/${request.id}`}>
                  <div
                    className="mx-2 my-4 rounded-md border border-slate-200 bg-white px-4 py-4 shadow-md transition-colors hover:bg-slate-50 dark:border dark:border-slate-700 dark:bg-slate-900 dark:hover:bg-slate-800"
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
                          )} mt-2 w-min rounded-md px-2 py-1 text-xs font-medium ring-1 ring-inset`}
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
            <Link to={"/new"}>
              <Button className="float-right">Create new Request</Button>
            </Link>
          </div>
        </div>
      )}
    </div>
  );
}

export { Requests, mapStatusToLabelColor, mapStatus, timeSince };
