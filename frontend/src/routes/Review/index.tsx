import { useEffect, useRef, useState } from "react";
import { Execute } from "../../api/ExecutionRequestApi";
import LiveSessionWebsockets from "../LiveSessionWebsockets";
import SessionHeader from "./SessionHeader";
import { sessionAccess } from "./sessionAccess";
import { useNavigate, useParams } from "react-router-dom";
import Breadcrumbs from "../../components/Breadcrumbs";
import Spinner from "../../components/Spinner";
import useRequest from "../../hooks/request";
import KubernetesRequestDisplay from "./KubernetesRequestDisplay";
import DatasourceRequestDisplay from "./DatasourceRequestDisplay";
import DatasourceRequestActions from "./DatasourceRequestActions";
import KubernetesRequestActions from "./KubernetesRequestActions";
import RequestSidebar from "./RequestSidebar";
import ActivityTimeline from "./ActivityTimeline";
import NotAuthorized from "../../components/NotAuthorized";

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
    execute,
    cancelQuery,
    closeRequest,
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

  const navigate = useNavigate();
  const routeParams = useParams();
  const sessionActive = routeParams["*"] === "session";
  const temporary =
    request?._type === "DATASOURCE" && request.type === "TemporaryAccess";
  const [sessionOpened, setSessionOpened] = useState(sessionActive);
  const [liveEvents, setLiveEvents] = useState<Execute[]>([]);
  const [now, setNow] = useState(Date.now());
  const refreshRef = useRef(refreshRequest);
  refreshRef.current = refreshRequest;
  const previousTab = useRef(sessionActive);
  useEffect(() => {
    if (sessionActive) setSessionOpened(true);
    if (previousTab.current !== sessionActive) void refreshRef.current();
    previousTab.current = sessionActive;
  }, [sessionActive]);
  useEffect(() => {
    if (!temporary) return;
    const clock = window.setInterval(() => setNow(Date.now()), 1000);
    const refresh = window.setInterval(() => void refreshRef.current(), 15000);
    return () => {
      window.clearInterval(clock);
      window.clearInterval(refresh);
    };
  }, [temporary]);
  const access = request ? sessionAccess(request, liveEvents, now) : undefined;

  const run = async (explain?: boolean, dryRun?: boolean) => {
    if (request?.type === "SingleExecution") {
      await execute(explain || false, dryRun || false);
    } else {
      void navigate(`/requests/${request?.id}/session`);
    }
  };

  return (
    <div>
      {(loading && <Spinner size="lg" page />) ||
        (request && (
          <div
            className={`mx-auto mt-6 px-4 pb-10 ${
              temporary ? "max-w-7xl" : "max-w-5xl"
            }`}
          >
            {temporary && access ? (
              <SessionHeader
                request={request}
                active={sessionActive}
                access={access}
              />
            ) : (
              <>
                <Breadcrumbs
                  items={[
                    { label: "Requests", to: "/requests" },
                    {
                      label: request.title,
                      to: sessionActive
                        ? `/requests/${encodeURIComponent(request.id)}`
                        : undefined,
                    },
                  ]}
                />
                <h1 className="my-2 text-3xl">{request?.title}</h1>
              </>
            )}
            {((temporary && sessionOpened) || sessionActive) && access && (
              <div hidden={!sessionActive}>
                <LiveSessionWebsockets
                  request={request}
                  expired={access.expired}
                  onEvents={setLiveEvents}
                  onRefresh={() => refreshRef.current()}
                />
              </div>
            )}
            {!sessionActive && (
              <div>
                <div className="flex flex-col gap-6 md:flex-row md:items-start">
                  <RequestSidebar request={request} sendReview={sendReview}>
                    {request._type === "DATASOURCE" ? (
                      <DatasourceRequestActions
                        request={request}
                        runQuery={run}
                        cancelQuery={cancelQuery}
                        startServer={start}
                      />
                    ) : (
                      <KubernetesRequestActions
                        request={request}
                        runQuery={run}
                      />
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
            )}
          </div>
        )) || (
          <div className="m-auto mt-10 max-w-3xl">
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
