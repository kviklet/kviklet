import {
  ProxyResponse,
  KubernetesExecutionRequestResponseWithComments,
  KubernetesExecuteResponse,
} from "../../api/ExecutionRequestApi";
import Spinner from "../../components/Spinner";
import ShellResult from "../../components/ShellResult";
import KubernetesRequestBox from "./KubernetesRequestBox";

function KubernetesRequestDisplay({
  request,
  run,
  start,
  updateRequest,
  results,
  dataLoading,
  executionError,
  proxyResponse,
}: {
  request: KubernetesExecutionRequestResponseWithComments;
  run: (explain?: boolean) => Promise<void>;
  start: () => Promise<void>;
  updateRequest: (request: { command?: string }) => Promise<void>;
  results: KubernetesExecuteResponse | undefined;
  dataLoading: boolean;
  executionError: string | undefined;
  proxyResponse: ProxyResponse | undefined;
}) {
  return (
    <>
      <KubernetesRequestBox
        request={request}
        runQuery={run}
        startServer={start}
        updateRequest={updateRequest}
      ></KubernetesRequestBox>
      <div className="flex justify-center">
        {(dataLoading && <Spinner></Spinner>) ||
          (results && <ShellResult {...results}></ShellResult>)}
      </div>
      {executionError && (
        <div className="my-4 text-red-500">{executionError}</div>
      )}
      {proxyResponse && (
        <div className="my-4 text-lime-500">
          Server started on {proxyResponse.port} with username{" "}
          <i>{proxyResponse.username}</i> and password{" "}
          <i>{proxyResponse.password}</i>
        </div>
      )}
    </>
  );
}

export default KubernetesRequestDisplay;
