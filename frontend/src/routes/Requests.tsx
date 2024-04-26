import { useEffect, useState } from "react";
import {
  ExecutionRequestResponse,
  getRequests,
} from "../api/ExecutionRequestApi";
import { Link } from "react-router-dom";
import Button from "../components/Button";
import Spinner from "../components/Spinner";
import { CircleStackIcon, CloudIcon } from "@heroicons/react/20/solid";

const Toggle = (props: { active: boolean; onClick: () => void }) => {
  return (
    <label
      className="relative inline-flex items-center cursor-pointer"
      onClick={props.onClick}
    >
      <input
        type="checkbox"
        checked={props.active}
        className="sr-only peer"
        readOnly
        onClick={(event) => event.stopPropagation()}
      />
      <div className="w-11 h-6 bg-slate-200 rounded-full peer dark:bg-slate-700 peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-0.5 after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all dark:border-gray-600 peer-checked:bg-indigo-800"></div>
    </label>
  );
};

function timeSince(date: Date) {
  const seconds = Math.floor((new Date().getTime() - date.getTime()) / 1000);

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

function mapStatus(status?: string) {
  switch (status) {
    case "APPROVED":
      return "Approved";
    case "AWAITING_APPROVAL":
      return "Waiting";
    case "PENDING":
      return "Pending";
    case "SUCCESS":
      return "Success";
    default:
      return "Unknown";
  }
}

function mapStatusToLabelColor(status?: string) {
  switch (status) {
    case "APPROVED":
      return "dark:ring-lime-400/10 dark:text-lime-500 ring-lime-500/10 text-lime-600 bg-lime-50 dark:bg-lime-400/10";
    case "AWAITING_APPROVAL":
      return "dark:ring-sky-400/10 dark:text-sky-500 ring-sky-500/10 text-sky-600 bg-sky-50 dark:bg-sky-400/10";
    case "PENDING":
      return "dark:ring-yellow-400/10 dark:text-yellow-500 ring-yellow-500/10 text-yellow-600 bg-yellow-50 dark:bg-yellow-400/10";
    case "SUCCESS":
      return "dark:ring-lime-400/10 dark:text-lime-500 ring-lime-500/10 text-lime-600 bg-lime-50 dark:bg-lime-400/10";
    default:
      return "dark:ring-gray-400/10 dark:text-gray-500 ring-gray-500/10 text-gray-600 bg-gray-50 dark:bg-gray-400/10";
  }
}

function Requests() {
  const [requests, setRequests] = useState<ExecutionRequestResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  useEffect(() => {
    const fetchData = async () => {
      setLoading(true);
      const requests = await getRequests();
      setRequests(requests);
      setLoading(false);
    };
    void fetchData();
  }, []);
  const [onlyPending, setOnlyPending] = useState(false);
  const visibleRequests = onlyPending
    ? requests.filter((r) => r.reviewStatus === "AWAITING_APPROVAL")
    : requests;

  const sortedRequests = [...visibleRequests].sort((a, b) => {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  return (
    <div className="h-full">
      <div className=" border-b border-slate-300 bg-slate-50 dark:bg-slate-950 dark:border-slate-700">
        <h1 className=" max-w-5xl mx-auto text-xl m-5 pl-1.5">
          {" "}
          Open Requests
        </h1>
      </div>
      {(loading && <Spinner></Spinner>) || (
        <div className="bg-slate-50 h-full dark:bg-slate-950">
          <div className="max-w-5xl mx-auto ">
            <div className="flex flex-row">
              <div className="ml-auto flex flex-row mb-2 mt-4 mr-2">
                <label className="mr-2">Only show pending requests</label>
                <Toggle
                  active={onlyPending}
                  onClick={() => setOnlyPending(!onlyPending)}
                ></Toggle>
              </div>
            </div>

            {visibleRequests.length === 0 && (
              <div className="my-4 mx-2 px-4 py-2">
                <h2 className="text-lg text-center">No open requests</h2>
              </div>
            )}
            {sortedRequests.map((request) => {
              return (
                <Link to={`/requests/${request.id}`}>
                  <div
                    className="shadow-md border border-slate-200 bg-white my-4 mx-2 px-4 py-4 dark:bg-slate-900 dark:border dark:border-slate-700 dark:hover:bg-slate-800 hover:bg-slate-50 rounded-md transition-colors"
                    key={request.id}
                  >
                    <div className="flex">
                      <div className="flex mb-2 flex-col">
                        <h2 className="text-md">{request.title}</h2>
                        <p className="dark:text-slate-400 text-slate-600">
                          {request.description}
                        </p>
                        {(request._type === "DATASOURCE" && (
                          <CircleStackIcon className="w-4 mt-auto dark:text-slate-600 text-slate-400"></CircleStackIcon>
                        )) || (
                          <CloudIcon className="w-4 mt-auto dark:text-slate-600 text-slate-400"></CloudIcon>
                        )}
                      </div>
                      <div className="ml-auto flex flex-col items-end">
                        <div className="mb-2 text-sm dark:text-slate-400 text-slate-600" title="{new Date(request.createdAt).toLocaleDateString()} UTC">
                          {timeSince(new Date(request.createdAt))}
                        </div>
                        <span
                          className={`${mapStatusToLabelColor(
                            request.reviewStatus,
                          )} w-min rounded-md px-2 py-1 mt-2 text-xs font-medium ring-1 ring-inset`}
                        >
                          {mapStatus(request?.reviewStatus)}
                        </span>
                        <span
                          className={`w-min rounded-md px-2 py-1 mt-2 text-xs font-medium  ring-1 ring-inset bg-yellow-50 text-yellow-600 ring-yellow-500/10 dark:bg-yellow-400/10 dark:text-yellow-500 dark:ring-yellow-400/20`}
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
