import { useEffect, useState } from "react";
import {
  ChangeExecutionRequestPayload,
  DatasourceExecutionRequestResponseWithComments,
  ExecuteResponseResult,
  ExecutionRequestResponseWithComments,
  KubernetesExecuteResponse,
  ProxyResponse,
  addCommentToRequest,
  addReviewToRequest,
  cancel,
  executeCommand,
  getSingleRequest,
  patchRequest,
  postStartServer,
  runQuery,
} from "../api/ExecutionRequestApi";
import useNotification from "./useNotification";
import { isApiErrorResponse } from "../api/Errors";
import { DatabaseType } from "../api/DatasourceApi";

enum ReviewTypes {
  Comment = "comment",
  Approve = "approve",
  RequestChange = "request_change",
  Reject = "reject",
}

const isRelationalDatabase = (
  request: DatasourceExecutionRequestResponseWithComments | undefined,
): boolean => {
  return (
    request?.connection?.type === DatabaseType.POSTGRES ||
    request?.connection?.type === DatabaseType.MYSQL ||
    request?.connection?.type === DatabaseType.MSSQL ||
    request?.connection?.type === DatabaseType.MARIADB
  );
};

const useRequest = (id: string) => {
  const [request, setRequest] = useState<
    ExecutionRequestResponseWithComments | undefined
  >(undefined);
  const [loading, setLoading] = useState(true);
  const [proxyResponse, setProxyResponse] = useState<ProxyResponse | undefined>(
    undefined,
  );

  const { addNotification } = useNotification();

  async function loadRequest() {
    setLoading(true);
    const request = await getSingleRequest(id);
    if (isApiErrorResponse(request)) {
      addNotification({
        title: "Failed to fetch request",
        text: request.message,
        type: "error",
      });
      setLoading(false);
      return;
    }
    setRequest(request);
    setLoading(false);
  }

  useEffect(() => {
    void loadRequest();
  }, []);

  const [results, setResults] = useState<ExecuteResponseResult[] | undefined>();
  const [dataLoading, setDataLoading] = useState<boolean>(false);
  const [kubernetesResults, setKubernetesResults] = useState<
    KubernetesExecuteResponse | undefined
  >();
  const [executionError, setExecutionError] = useState<string | undefined>(
    undefined,
  );

  const addComment = async (comment: string) => {
    const response = await addCommentToRequest(id, comment);

    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to add comment",
        text: response.message,
        type: "error",
      });
      return;
    }

    // update the request with the new comment by updating the events propertiy with a new Comment
    setRequest((request) => {
      if (request === undefined) {
        return undefined;
      }
      return {
        ...request,
        events: [...request.events, response],
      };
    });
  };

  const start = async (): Promise<void> => {
    const response = await postStartServer(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to start proxy",
        text: response.message,
        type: "error",
      });
    } else {
      setProxyResponse(response);
    }
  };

  const updateRequest = async (request: ChangeExecutionRequestPayload) => {
    const response = await patchRequest(id, request);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to update request",
        text: response.message,
        type: "error",
      });
      return;
    } else {
      setRequest(response);
    }
  };

  const sendReview = async (
    comment: string,
    type: ReviewTypes,
  ): Promise<boolean> => {
    let response;
    switch (type) {
      case ReviewTypes.Approve:
        response = await addReviewToRequest(id, comment, "APPROVE");
        break;
      case ReviewTypes.Comment:
        response = await addComment(comment);
        break;
      case ReviewTypes.RequestChange:
        response = await addReviewToRequest(id, comment, "REQUEST_CHANGE");
        break;
      case ReviewTypes.Reject:
        response = await addReviewToRequest(id, comment, "REJECT");
        break;
    }
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to add review",
        text: response.message,
        type: "error",
      });
      return false;
    }
    await loadRequest();
    return true;
  };

  const execute = async (explain: boolean) => {
    setDataLoading(true);
    if (request?._type === "DATASOURCE") {
      const response = await runQuery(id, undefined, explain);
      if (isApiErrorResponse(response)) {
        setExecutionError(response.message);
      } else {
        setResults(response.results);
      }
    } else if (request?._type === "KUBERNETES") {
      const response = await executeCommand(id);
      if (isApiErrorResponse(response)) {
        setExecutionError(response.message);
      } else {
        setKubernetesResults(response);
      }
    }

    setDataLoading(false);
  };

  const cancelQuery = async () => {
    setDataLoading(true);
    const response = await cancel(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to cancel query",
        text: response.message,
        type: "error",
      });
    } else {
      addNotification({
        title: "Query cancelled",
        text: "The query was successfully cancelled",
        type: "info",
      });
    }
  };

  return {
    request,
    sendReview,
    execute,
    cancelQuery,
    start,
    updateRequest,
    results,
    kubernetesResults,
    dataLoading,
    executionError,
    loading,
    proxyResponse,
    isRelationalDatabase,
  };
};

export default useRequest;

export { ReviewTypes, isRelationalDatabase };
