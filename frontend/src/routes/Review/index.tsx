import { useNavigate, useParams } from "react-router-dom";
import { mapStatus, mapStatusToLabelColor } from "../Requests";
import Spinner from "../../components/Spinner";
import useRequest from "../../hooks/request";
import KubernetesRequestDisplay from "./KubernetesRequestDisplay";
import DatasourceRequestDisplay from "./DatasourceRequestDisplay";
import EditEvent from "./events/EditEvent";
import ExecuteEvent from "./events/ExecuteEvent";
import ReviewEvent from "./events/ReviewEvent";
import Comment from "./events/Comment";
import CommentBox from "./CommentBox";

interface RequestReviewParams {
  requestId: string;
}

function RequestReview() {
  const params = useParams() as unknown as RequestReviewParams;
  const {
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
  } = useRequest(params.requestId);

  const navigate = useNavigate();

  const run = async (explain?: boolean) => {
    if (request?.type === "SingleExecution") {
      await execute(explain || false);
    } else {
      if (request?.liveSessionEnabled) {
        navigate(`/requests/${request?.id}/session-live`);
      } else {
        navigate(`/requests/${request?.id}/session`);
      }
    }
  };

  return (
    <div>
      {(loading && <Spinner />) ||
        (request && (
          <div className="m-auto mt-10 max-w-3xl">
            <h1 className="my-2 flex w-full items-start text-3xl">
              <div className="mr-auto">{request?.title}</div>
              <div
                className={` ${mapStatusToLabelColor(
                  mapStatus(request.reviewStatus, request.executionStatus),
                )} mt-2 rounded-md px-2 py-1 text-base font-medium ring-1 ring-inset `}
              >
                {mapStatus(request.reviewStatus, request.executionStatus)}
              </div>
            </h1>
            <div className="">
              <div className="">
                {request &&
                  (request?._type === "DATASOURCE" ? (
                    <DatasourceRequestDisplay
                      request={request}
                      run={run}
                      cancelQuery={cancelQuery}
                      start={start}
                      updateRequest={updateRequest}
                      results={results}
                      dataLoading={dataLoading}
                      executionError={executionError}
                      proxyResponse={proxyResponse}
                    ></DatasourceRequestDisplay>
                  ) : (
                    (
                      <KubernetesRequestDisplay
                        request={request}
                        run={run}
                        start={start}
                        updateRequest={updateRequest}
                        results={kubernetesResults}
                        dataLoading={dataLoading}
                        executionError={executionError}
                        proxyResponse={proxyResponse}
                      ></KubernetesRequestDisplay>
                    ) || <></>
                  ))}
                <div className="mt-3 w-full border-b border-slate-300 dark:border-slate-700"></div>
                <div className="mt-6">
                  <span>Activity</span>
                </div>
                <div>
                  {request === undefined
                    ? ""
                    : request?.events?.map((event, index) => {
                        if (event?._type === "EDIT")
                          return (
                            <EditEvent event={event} index={index}></EditEvent>
                          );
                        if (event?._type === "EXECUTE")
                          return (
                            <ExecuteEvent
                              event={event}
                              index={index}
                            ></ExecuteEvent>
                          );
                        if (event?._type === "COMMENT")
                          return (
                            <Comment event={event} index={index}></Comment>
                          );
                        if (event?._type === "REVIEW")
                          return (
                            <ReviewEvent
                              event={event}
                              index={index}
                            ></ReviewEvent>
                          );
                      })}
                  <CommentBox
                    sendReview={sendReview}
                    userId={request?.author?.id}
                  ></CommentBox>
                </div>
              </div>
            </div>
          </div>
        ))}
    </div>
  );
}

export default RequestReview;
