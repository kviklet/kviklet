import {
  ProxyResponse,
  ExecuteResponseResult,
  DatasourceExecutionRequestResponseWithComments,
} from "../../api/ExecutionRequestApi";
import MultiResult from "../../components/MultiResult";
import Spinner from "../../components/Spinner";
import DatasourceRequestBox from "./DatasourceRequestBox";

function DatasourceRequestDisplay({
  request,
  run,
  start,
  cancelQuery,
  updateRequest,
  results,
  dataLoading,
  executionError,
  proxyResponse,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  run: (explain?: boolean) => Promise<void>;
  cancelQuery: () => Promise<void>;
  start: () => Promise<void>;
  updateRequest: (request: { statement?: string }) => Promise<void>;
  results: ExecuteResponseResult[] | undefined;
  dataLoading: boolean;
  executionError: string | undefined;
  proxyResponse: ProxyResponse | undefined;
}) {
  return (
    <>
      <DatasourceRequestBox
        request={request}
        runQuery={run}
        cancelQuery={cancelQuery}
        startServer={start}
        updateRequest={updateRequest}
      ></DatasourceRequestBox>
      <div className="mt-4 flex justify-center">
        {(dataLoading && <Spinner></Spinner>) ||
          (results && <MultiResult resultList={results}></MultiResult>)}
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

export default DatasourceRequestDisplay;
