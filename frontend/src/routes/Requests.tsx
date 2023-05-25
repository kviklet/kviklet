import { useEffect, useState } from "react";
import {
  ExecutionRequestResponse,
  getRequests,
} from "../api/ExecutionRequestApi";
import { Link } from "react-router-dom";

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
        {requests.map((request) => {
          return (
            <Link to={`/requests/${request.id}`}>
              <div className="shadow-md my-4 mx-2 px-4 py-2" key={request.id}>
                <h2 className="text-lg font-bold">{request.title}</h2>
                <p>{request.description}</p>
              </div>
            </Link>
          );
        })}
        <Link to={"/requests/new"}>
          <button className="bg-blue-500 hover:bg-blue-700 mx-2 text-white font-bold py-2 px-4 rounded">
            Create new Request
          </button>
        </Link>
      </div>
    </div>
  );
}

export { Requests };
