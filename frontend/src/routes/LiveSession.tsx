import { useContext, useEffect, useRef, useState } from "react";
import {
  ExecuteResponseResult,
  ExecutionRequestResponseWithComments,
  runQuery,
} from "../api/ExecutionRequestApi";
import Button from "../components/Button";

import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";
import Spinner from "../components/Spinner";
import { useParams } from "react-router-dom";
import MultiResult from "../components/MultiResult";
import { isApiErrorResponse } from "../api/Errors";
import baseUrl from "../api/base";
import useRequest, { isRelationalDatabase } from "../hooks/request";
import LoadingCancelButton from "../components/LoadingCancelButton";

interface SessionParams {
  requestId: string;
}

export default function LiveSession() {
  const params = useParams() as unknown as SessionParams;
  const { request, cancelQuery } = useRequest(params.requestId);

  return (
    <div className="flex h-full w-full">
      <div className="mx-auto flex h-full w-2/3 flex-col">
        {(request && (
          <Editor request={request} cancelQuery={cancelQuery}></Editor>
        )) || <Spinner></Spinner>}
      </div>
    </div>
  );
}

const Editor = ({
  request,
  cancelQuery,
}: {
  request: ExecutionRequestResponseWithComments;
  cancelQuery: () => Promise<void>;
}) => {
  const [results, setResults] = useState<ExecuteResponseResult[] | undefined>(
    undefined,
  );
  const [dataLoading, setDataLoading] = useState(false);
  const [updatedRows, setUpdatedRows] = useState<number | undefined>(undefined);
  const [executionError, setExecutionError] = useState<string | undefined>(
    undefined,
  );

  const [editor, setEditor] =
    useState<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoEl = useRef(null);

  const { currentTheme } = useContext<ThemeContext>(ThemeStatusContext);
  const theme = currentTheme === "dark" ? "vs-dark" : "vs";

  const language =
    request._type === "DATASOURCE" && isRelationalDatabase(request)
      ? "sql"
      : "json";

  useEffect(() => {
    if (monacoEl) {
      setEditor((editor) => {
        if (editor) return editor;

        const newEditor = monaco.editor.create(monacoEl.current!, {
          value: "",
          language: language,
          suggest: {
            showKeywords: true,
          },

          automaticLayout: true,
          minimap: { enabled: false },
        });
        return newEditor;
      });
    }

    return () => editor?.dispose();
  }, [monacoEl.current]);
  editor?.addCommand(monaco.KeyMod.Shift | monaco.KeyCode.Enter, function () {
    void executeQuery();
  });
  monaco.editor.setTheme(theme);

  const handleClick = (event: React.MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();

    const selection = editor?.getSelection();
    const query =
      (selection && editor?.getModel()?.getValueInRange(selection)) ||
      editor?.getValue();

    const downloadUrl = `${baseUrl}/execution-requests/${
      request.id
    }/download?query=${encodeURIComponent(query || "")}`;

    window.location.href = downloadUrl;
  };

  const executeQuery = async () => {
    const selection = editor?.getSelection();
    const text =
      (selection && editor?.getModel()?.getValueInRange(selection)) ||
      editor?.getValue();

    setUpdatedRows(undefined);
    setExecutionError(undefined);
    setResults(undefined);
    setDataLoading(true);
    const response = await runQuery(request.id, text);
    if (isApiErrorResponse(response)) {
      setExecutionError(response.message);
    } else {
      setResults(response.results);
    }

    setDataLoading(false);
  };

  return (
    <div className="flex h-full w-full">
      <div className="mx-auto flex h-full w-2/3 flex-col">
        <div className="my-5 h-32 resize-y overflow-auto">
          <div className="h-full w-full" ref={monacoEl}></div>
        </div>
        <div className="flex flex-row">
          {request?._type === "DATASOURCE" && isRelationalDatabase(request) && (
            <a className="ml-auto mr-2" href="#" onClick={handleClick}>
              <Button>Download as CSV</Button>
            </a>
          )}
          {request?._type === "DATASOURCE" && isRelationalDatabase(request) ? (
            <LoadingCancelButton
              className=""
              id="runQuery"
              type="submit"
              disabled={request?.reviewStatus !== "APPROVED"}
              onClick={executeQuery}
              onCancel={() => void cancelQuery()}
            >
              <div className="play-triangle mr-2 inline-block h-3 w-2 bg-slate-50"></div>
              Run Query
            </LoadingCancelButton>
          ) : (
            <Button type="submit" onClick={() => void executeQuery()}>
              {" "}
              Run Query
            </Button>
          )}
        </div>

        {updatedRows && (
          <div className="text-slate-500">{updatedRows} rows updated</div>
        )}
        {executionError && <div className="text-red-500">{executionError}</div>}
        <div className="flex h-full justify-center">
          {(dataLoading && <Spinner></Spinner>) ||
            (results && <MultiResult resultList={results}></MultiResult>)}
        </div>
      </div>
    </div>
  );
};
