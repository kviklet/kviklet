import { useContext, useEffect, useRef, useState } from "react";
import { ExecuteResponseResult, runQuery } from "../api/ExecutionRequestApi";
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

interface SessionParams {
  requestId: string;
}

export default function LiveSession() {
  const params = useParams() as unknown as SessionParams;
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

  useEffect(() => {
    if (monacoEl) {
      setEditor((editor) => {
        if (editor) return editor;

        const newEditor = monaco.editor.create(monacoEl.current!, {
          value: "SELECT * FROM TEST;",
          language: "sql",
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

  const executeQuery = async () => {
    const selection = editor?.getSelection();
    const text =
      (selection && editor?.getModel()?.getValueInRange(selection)) ||
      editor?.getValue();

    setUpdatedRows(undefined);
    setExecutionError(undefined);
    setResults(undefined);
    setDataLoading(true);
    const response = await runQuery(params.requestId, text);
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
        <div className="flex flex-row-reverse">
          <Button type="submit" onClick={() => void executeQuery()}>
            {" "}
            Run Query
          </Button>
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
}
