import React, { useEffect, useRef, useState } from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";
import Button from "../components/Button";
import MultiResult from "../components/MultiResult";
import Spinner from "../components/Spinner";
import { ExecuteResponseResult } from "../api/ExecutionRequestApi";
import useRequest from "../hooks/request";
import { useParams } from "react-router-dom";
import { websocketBaseUrl } from "../api/base";

interface LiveSessionWebsocketsProps {
  requestId: string;
  initialLanguage: string;
}

interface SessionParams {
  requestId: string;
}

const LiveSessionWebsocketsLoader: React.FC = () => {
  const params = useParams() as unknown as SessionParams;
  const { request, cancelQuery } = useRequest(params.requestId);

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
  const [results, setResults] = useState<ExecuteResponseResult[] | undefined>(
    undefined,
  );
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | undefined>(undefined);
  const [updatedRows, setUpdatedRows] = useState<number | undefined>(undefined);
  const monacoEl = useRef(null);
  const ws = useRef<WebSocket | null>(null);

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
    }

    // Initialize WebSocket connection
    const socket = new WebSocket(`${websocketBaseUrl}/sql/${requestId}`);

    socket.onopen = () => {
      console.log("WebSocket connection established");
      setError(undefined);
    };

    socket.onmessage = (event) => {
      const data = JSON.parse(event.data);
      handleWebSocketMessage(data);
    };

    socket.onerror = (error) => {
      console.error("WebSocket error:", error);
      setError("WebSocket connection error");
    };

    socket.onclose = () => {
      console.log("WebSocket connection closed");
    };

    ws.current = socket;

    return () => {
      editor?.dispose();
      socket.close();
    };
  }, [requestId, initialLanguage]);

  const handleWebSocketMessage = (data: any) => {
    if (data.type === "results") {
      setResults(data.results);
      setIsLoading(false);
    } else if (data.type === "error") {
      setError(data.message);
      setIsLoading(false);
    } else if (data.type === "updatedRows") {
      setUpdatedRows(data.count);
    }
  };

  const executeQuery = () => {
    const query = editor?.getValue();
    if (query && ws.current?.readyState === WebSocket.OPEN) {
      setIsLoading(true);
      setError(undefined);
      setResults(undefined);
      setUpdatedRows(undefined);
      ws.current.send(JSON.stringify({ type: "execute", query }));
    }
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
          <Button onClick={executeQuery} disabled={isLoading}>
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
