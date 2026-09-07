import { useEffect, useRef, useState } from "react";
import { Execute } from "../../api/ExecutionRequestApi";
import LiveSessionWebsockets from "../LiveSessionWebsockets";
import { sessionAccess } from "./sessionAccess";
import { useParams } from "react-router-dom";
import Breadcrumbs from "../../components/Breadcrumbs";
import NotAuthorized from "../../components/NotAuthorized";
import Spinner from "../../components/Spinner";
import useRequest from "../../hooks/request";
import ActivityTimeline from "./ActivityTimeline";
import DatasourceRequestActions from "./DatasourceRequestActions";
import DatasourceRequestDisplay from "./DatasourceRequestDisplay";
import KubernetesRequestActions from "./KubernetesRequestActions";
import KubernetesRequestDisplay from "./KubernetesRequestDisplay";
import RequestSidebar from "./RequestSidebar";

interface RequestReviewParams {
  requestId: string;
}

function RequestReview() {
  const { requestId } = useParams();
  return <RequestReviewContent key={requestId} />;
}

function RequestReviewContent() {
  const params = useParams() as unknown as RequestReviewParams;
  const {
    request,
    sendReview,
    closeRequest,
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
    refreshRequest,
  } = useRequest(params.requestId);

  const temporary = request?.type === "TemporaryAccess";
  const [liveEvents, setLiveEvents] = useState<Execute[]>([]);
  const [now, setNow] = useState(Date.now());
  const refreshRef = useRef(refreshRequest);
  refreshRef.current = refreshRequest;
  // The access window is derived client-side so the sidebar counts down and
  // flips to expired without a reload; the periodic refresh picks up reviews
  // and executions made elsewhere.
  useEffect(() => {
    if (!temporary) return;
    const clock = window.setInterval(() => setNow(Date.now()), 10_000);
    const refresh = window.setInterval(() => void refreshRef.current(), 15_000);
    return () => {
      window.clearInterval(clock);
      window.clearInterval(refresh);
    };
  }, [temporary]);
  const access =
    request && temporary ? sessionAccess(request, liveEvents, now) : undefined;

  const run = async (explain?: boolean, dryRun?: boolean) => {
    await execute(explain || false, dryRun || false);
  };

  return (
    <div>
      {(loading && <Spinner size="lg" page />) ||
        (request && (
          <div className="m-auto mt-10 max-w-5xl px-4 xl:px-0">
            <Breadcrumbs
              className="mb-4"
              items={[
                { label: "Requests", to: "/requests" },
                { label: request.title },
              ]}
            />
            <div className="flex flex-col gap-6 md:flex-row md:items-start">
              <RequestSidebar
                request={request}
                sendReview={sendReview}
                access={access}
              >
                {request._type === "DATASOURCE" ? (
                  <DatasourceRequestActions
                    request={request}
                    runQuery={run}
                    cancelQuery={cancelQuery}
                    startServer={start}
                  />
                ) : (
                  <KubernetesRequestActions request={request} runQuery={run} />
                )}
              </RequestSidebar>
              <div className="min-w-0 flex-1">
                {request._type === "DATASOURCE" ? (
                  <DatasourceRequestDisplay
                    request={request}
                    updateRequest={updateRequest}
                    results={results}
                    dataLoading={dataLoading}
                    executionError={executionError}
                    proxyResponse={proxyResponse}
                  ></DatasourceRequestDisplay>
                ) : (
                  <KubernetesRequestDisplay
                    request={request}
                    updateRequest={updateRequest}
                    results={kubernetesResults}
                    dataLoading={dataLoading}
                    executionError={executionError}
                    proxyResponse={proxyResponse}
                  ></KubernetesRequestDisplay>
                )}
                {access &&
                  // The session takes the statement's place. It only connects once
                  // the request is approved; before that the page has nothing to
                  // sync and reviewers should not open sessions on pending requests.
                  (request.reviewStatus === "APPROVED" ? (
                    <LiveSessionWebsockets
                      request={request}
                      expired={access.expired}
                      onEvents={setLiveEvents}
                      onRefresh={() => refreshRef.current()}
                    />
                  ) : (
                    <div
                      className="rounded border border-dashed border-slate-300 px-4 py-8 text-center text-sm text-slate-500 dark:border-slate-700 dark:text-slate-400"
                      data-testid="session-placeholder"
                    >
                      {request.reviewStatus === "REJECTED"
                        ? "This request is closed."
                        : "The session editor opens here once the request is approved."}
                    </div>
                  ))}
                <div className="mt-3 w-full border-b border-slate-300 dark:border-slate-700"></div>
                <ActivityTimeline
                  request={request}
                  sendReview={sendReview}
                  websocketEvents={liveEvents}
                  closeRequest={closeRequest}
                />
              </div>
            </div>
          </div>
        )) || (
          <div className="m-auto mt-10 max-w-3xl px-4 md:px-0">
            <NotAuthorized
              resource="this request"
              message="It may not exist, or your role has no access to its connection."
            />
          </div>
        )}
    </div>
  );
}

export default RequestReview;
