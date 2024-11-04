import React, { useEffect, useRef, useState } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import Button from "../components/Button";
import MultiResult from "../components/MultiResult";
import Spinner from "../components/Spinner";
import useRequest from "../hooks/request";
import { useParams } from "react-router-dom";
import useLiveSession from "../hooks/useLiveSession";
import useNotification from "../hooks/useNotification";
import EventHistory from "./Review/EventHistory";

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

  const { addNotification } = useNotification();
  const updateEditorContent = (newContent: string) => {
    const currentContent = monaco.editor.getModels()[0].getValue();
    if (currentContent !== newContent) {
      monaco.editor.getModels()[0].setValue(newContent);
    }
  };

  const { executeQuery, updateContent, isLoading, updatedRows, results } =
    useLiveSession(requestId, updateEditorContent);

  const { request } = useRequest(requestId);

  useEffect(() => {
    if (monacoEl.current) {
      const newEditor = monaco.editor.create(monacoEl.current, {
        value: "",
        language: initialLanguage,
        theme: "vs-dark",
        minimap: { enabled: false },
      });
      setEditor(newEditor);

      const disposable = newEditor.onDidChangeModelContent((e) => {
        if (e.isFlush) {
          // Ignore updates that are not user initiated e.g. our own update call
          return;
        }
        const newContent = newEditor.getValue();
        updateContent(newContent);
      });

      return () => {
        disposable.dispose();
        newEditor.dispose();
      };
    }
  }, [requestId, initialLanguage]);

  const onExecuteQueryClick = () => {
    const selection = editor?.getSelection();
    const text =
      (selection && editor?.getModel()?.getValueInRange(selection)) ||
      editor?.getValue();
    if (!text) {
      addNotification({
        type: "error",
        title: "Query Error",
        text: "Cannot execute an empty query",
      });
      return;
    }
    executeQuery(text);
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
        {updatedRows !== undefined && (
          <div className="mb-4 text-green-500">{updatedRows} rows updated</div>
        )}
        <div className="flex h-full justify-center">
          {(isLoading && <Spinner></Spinner>) ||
            (results && <MultiResult resultList={results}></MultiResult>)}
        </div>

        {request && <EventHistory request={request} reverse={true} />}
      </div>
    </div>
  );
};

export default LiveSessionWebsocketsLoader;
