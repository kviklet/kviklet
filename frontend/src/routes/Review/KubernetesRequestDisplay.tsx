import {
  ProxyResponse,
  KubernetesExecutionRequestResponseWithComments,
  KubernetesExecuteResponse,
} from "../../api/ExecutionRequestApi";
import Spinner from "../../components/Spinner";
import ShellResult from "../../components/ShellResult";
import KubernetesRequestBox from "./KubernetesRequestBox";
import ProxyConnectionCard from "./ProxyConnectionCard";

function KubernetesRequestDisplay({
  request,
  updateRequest,
  results,
  dataLoading,
  executionError,
  proxyResponse,
}: {
  request: KubernetesExecutionRequestResponseWithComments;
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
        updateRequest={updateRequest}
      ></KubernetesRequestBox>
      <div className="mt-4 flex justify-center">
        {(dataLoading && <Spinner></Spinner>) ||
          (results && <ShellResult {...results}></ShellResult>)}
      </div>
      {executionError && (
        <div className="my-4 text-red-500">{executionError}</div>
      )}
      {proxyResponse && <ProxyConnectionCard proxy={proxyResponse} />}
    </>
  );
}

export default KubernetesRequestDisplay;
