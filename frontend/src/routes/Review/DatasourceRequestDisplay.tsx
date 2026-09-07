import {
  ExecuteResponseResult,
  DatasourceExecutionRequestResponseWithComments,
} from "../../api/ExecutionRequestApi";
import MultiResult from "../../components/MultiResult";
import Spinner from "../../components/Spinner";
import DatasourceRequestBox from "./DatasourceRequestBox";

function DatasourceRequestDisplay({
  request,
  updateRequest,
  results,
  dataLoading,
  executionError,
}: {
  request: DatasourceExecutionRequestResponseWithComments | undefined;
  updateRequest: (request: { statement?: string }) => Promise<void>;
  results: ExecuteResponseResult[] | undefined;
  dataLoading: boolean;
  executionError: string | undefined;
}) {
  return (
    <>
      <DatasourceRequestBox
        request={request}
        updateRequest={updateRequest}
      ></DatasourceRequestBox>
      <div className="mt-4">
        {(dataLoading && (
          <div className="flex justify-center py-6">
            <Spinner />
          </div>
        )) ||
          (results && results.length > 0 && (
            <MultiResult resultList={results} />
          ))}
      </div>
      {executionError && (
        <div className="my-4 text-red-600 dark:text-red-400">
          {executionError}
        </div>
      )}
    </>
  );
}

export default DatasourceRequestDisplay;
