import React, { useCallback, useEffect, useRef, useState } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import Button from "../components/Button";
import MultiResult from "../components/MultiResult";
import Spinner from "../components/Spinner";
import useRequest from "../hooks/request";
import { useParams } from "react-router-dom";
import useLiveSession from "../hooks/useLiveSession";

interface LiveSessionWebsocketsProps {
  requestId: string;
  initialLanguage: string;
}

interface SessionParams {
  requestId: string;
}

const LiveSessionWebsocketsLoader: React.FC = () => {
  const params = useParams() as unknown as SessionParams;
  const { request } = useRequest(params.requestId);

  return (
    <>
      {request ? (
        <LiveSessionWebsockets
          requestId={params.requestId}
          initialLanguage={"sql"}
        />
      ) : (
        <div>Loading...</div>
      )}
    </>
  );
};

const LiveSessionWebsockets: React.FC<LiveSessionWebsocketsProps> = ({
  requestId,
  initialLanguage,
}) => {
  const [editor, setEditor] =
    useState<monaco.editor.IStandaloneCodeEditor | null>(null);
  const monacoEl = useRef(null);

  const [mode, setMode] = useState<"listen" | "write">("listen");
  const updateEditorContent = (newContent: string) => {
    if (mode === "listen") {
      monaco.editor.getModels()[0].setValue(newContent);
    }
  };

  const {
    error,
    executeQuery,
    updateContent,
    isLoading,
    updatedRows,
    results,
  } = useLiveSession(requestId, updateEditorContent);

  useEffect(() => {
    // Initialize Monaco editor
    if (monacoEl.current) {
      const newEditor = monaco.editor.create(monacoEl.current, {
        value: "",
        language: initialLanguage,
        theme: "vs-dark",
        minimap: { enabled: false },
      });
      setEditor(newEditor);

      // Add listener for content changes
      const disposable = newEditor.onDidChangeModelContent(() => {
        if (mode === "write") {
          const newContent = newEditor.getValue();
          updateContent(newContent);
        }
        //const newContent = newEditor.getValue();
        //updateContent(newContent);
      });

      return () => {
        disposable.dispose();
        newEditor.dispose();
      };
    }
  }, [requestId, initialLanguage]);

  const onExecuteQueryClick = () => {
    console.log("Setting mode to write");
    setMode("write");
  };

  return (
    <div className="flex h-full flex-col">
      <div className="mx-auto flex h-full w-2/3 flex-col">
        <div
          className="my-5 h-64 resize-y overflow-auto"
          data-testid="monaco-editor-wrapper"
        >
          <div className="h-full w-full" ref={monacoEl}></div>
        </div>
        <div className="mb-4 flex justify-end">
          <Button
            onClick={onExecuteQueryClick}
            type={(isLoading && "disabled") || "button"}
          >
            {isLoading ? "Running..." : "Run Query"}
          </Button>
        </div>
        {error && <div className="mb-4 text-red-500">{error}</div>}
        {updatedRows !== undefined && (
          <div className="mb-4 text-green-500">{updatedRows} rows updated</div>
        )}
        {isLoading ? (
          <Spinner />
        ) : (
          results && <MultiResult resultList={results} />
        )}
      </div>
    </div>
  );
};

export default LiveSessionWebsocketsLoader;
