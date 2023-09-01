import { MouseEvent, useEffect, useState } from "react";
import {
  ExecutionRequestResponse,
  getRequests,
} from "../api/ExecutionRequestApi";
import { Link } from "react-router-dom";
import Button from "../components/Button";

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

function mapStatusToColor(status?: string) {
  switch (status) {
    case "APPROVED":
      return "bg-lime-400";
    case "AWAITING_APPROVAL":
      return "bg-sky-400";
    case "PENDING":
      return "bg-yellow-400";
    case "SUCCESS":
      return "bg-lime-400";
    default:
      return "bg-gray-400";
  }
}

function Requests() {
  const [requests, setRequests] = useState<ExecutionRequestResponse[]>([]);
  useEffect(() => {
    const fetchData = async () => {
      const requests = await getRequests();
      setRequests(requests);
    };
    fetchData();
  }, []);
  const [onlyPending, setOnlyPending] = useState(false);

  const visibleRequests = onlyPending
    ? requests.filter((r) => r.reviewStatus === "AWAITING_APPROVAL")
    : requests;

  const sortedRequests = visibleRequests.sort((a, b) => {
    return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
  });

  return (
    <div className=" h-full">
      <div className=" border-b border-slate-300 bg-slate-50 dark:bg-slate-950 dark:border-slate-700">
        <h1 className=" max-w-5xl mx-auto text-xl m-5 pl-1.5">
          {" "}
          Open Requests
        </h1>
      </div>
      <div className="bg-slate-100 h-full dark:bg-slate-950">
        <div className="max-w-5xl mx-auto ">
          <div className="flex flex-row">
            <div className="flex flex-row">
              <input
                type="checkbox"
                className="mr-2"
                checked={onlyPending}
                onChange={(e) => setOnlyPending(e.target.checked)}
              />
              <label>Only show pending requests</label>
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
                  className="shadow-md border border-slate-200 bg-slate-50 my-4 mx-2 px-4 py-4 dark:bg-slate-900 dark:border dark:border-slate-700 dark:hover:bg-slate-800 hover:bg-slate-100 rounded-md transition-colors"
                  key={request.id}
                >
                  <div className="flex">
                    <div className="flex mb-2 flex-col">
                      <h2 className="text-md">{request.title}</h2>
                      <p className="dark:text-slate-400 text-slate-600">
                        {request.description}
                      </p>
                    </div>
                    <div className="ml-auto flex flex-col items-end">
                      <div className="mb-2 text-sm dark:text-slate-400 text-slate-600">
                        {timeSince(new Date(request.createdAt))}
                      </div>
                      <div
                        className={`${mapStatusToColor(
                          request.reviewStatus
                        )} font-bold rounded-full text-sm text-center w-20 text-white py-1 px-1.5`}
                      >
                        {mapStatus(request?.reviewStatus)}
                      </div>
                    </div>
                  </div>
                </div>
              </Link>
            );
          })}
          <Link to={"/requests/new"}>
            <Button className="float-right">Create new Request</Button>
          </Link>
        </div>
      </div>
    </div>
  );
}

export { Requests, mapStatusToColor };
