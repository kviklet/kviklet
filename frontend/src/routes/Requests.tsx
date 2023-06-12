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

function Requests() {
  const [requests, setRequests] = useState<ExecutionRequestResponse[]>([]);
  useEffect(() => {
    const fetchData = async () => {
      const requests = await getRequests();
      setRequests(requests);
    };
    fetchData();
  }, []);

  return (
    <div>
      <div className="max-w-5xl mx-auto">
        <h1 className="text-2xl font-bold m-5 pl-1.5"> Open Requests</h1>

        {requests.length === 0 && (
          <div className="my-4 mx-2 px-4 py-2">
            <h2 className="text-lg text-center">No open requests</h2>
          </div>
        )}
        {requests.map((request) => {
          return (
            <Link to={`/requests/${request.id}`}>
              <div className="shadow-md my-4 mx-2 px-4 py-2" key={request.id}>
                <h2 className="text-lg font-bold">{request.title}</h2>
                <p>{request.description}</p>
                <div className="flex">
                  <div className="ml-auto">
                    {timeSince(new Date(request.createdAt))}
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
  );
}

export { Requests };
