import { useContext, useEffect, useRef, useState } from "react";
import {
  ErrorResponse,
  SelectExecuteResponse,
  runQuery,
} from "../api/ExecutionRequestApi";
import Table from "../components/Table";
import Button from "../components/Button";

import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import {
  ThemeContext,
  ThemeStatusContext,
} from "../components/ThemeStatusProvider";
import Spinner from "../components/Spinner";
import { useParams } from "react-router-dom";

interface SessionParams {
  requestId: string;
}

export default function LiveSession() {
  const params = useParams() as unknown as SessionParams;
  const [data, setData] = useState<SelectExecuteResponse>({
    columns: [],
    data: [],
    _type: "select",
  });
  const [dataLoading, setDataLoading] = useState(false);
  const [updatedRows, setUpdatedRows] = useState<number | undefined>(undefined);
  const [executionError, setExecutionError] = useState<
    ErrorResponse | undefined
  >(undefined);

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

    console.log("executing: " + text);
    setUpdatedRows(undefined);
    setExecutionError(undefined);
    setData({
      columns: [],
      data: [],
      _type: "select",
    });
    setDataLoading(true);
    const result = await runQuery(params.requestId, text);
    switch (result._type) {
      case "select":
        setData(result);
        break;
      case "update":
        setUpdatedRows(result.rowsUpdated);
        break;
      case "error":
        setExecutionError(result);
        break;
    }
    setDataLoading(false);
  };

  return (
    <div className="w-full flex h-full">
      <div className="flex flex-col h-full mx-auto w-2/3">
        <div className="my-5 overflow-auto resize-y h-32">
          <div className="w-full h-full" ref={monacoEl}></div>
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
        {executionError && (
          <div className="text-red-500">
            {executionError.errorCode}: {executionError.message}
          </div>
        )}
        <div className="flex justify-center h-full">
          {(dataLoading && <Spinner></Spinner>) ||
            (data && <Table data={data}></Table>)}
        </div>
      </div>
    </div>
  );
}
