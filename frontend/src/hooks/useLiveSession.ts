import { useEffect, useRef, useState } from "react";
import { websocketBaseUrl } from "../api/base";
import { ExecuteResponseResult } from "../api/ExecutionRequestApi";
import {
  executeStatementMessage,
  resultMessage,
  statusMessage,
  updateContentMessage,
} from "../api/LiveSessionApi";
import { z } from "zod";
import debounce from "lodash/debounce";

const useLiveSession = (
  requestId: string,
  setContent: (content: string) => void,
) => {
  const ws = useRef<WebSocket | null>(null);
  const [error, setError] = useState<string | undefined>(undefined);
  const [results, setResults] = useState<ExecuteResponseResult[] | undefined>(
    undefined,
  );
  const [isLoading, setIsLoading] = useState(false);
  const [updatedRows, setUpdatedRows] = useState<number | undefined>(undefined);

  useEffect(() => {
    // Initialize WebSocket connection
    const socket = new WebSocket(`${websocketBaseUrl}/sql/${requestId}`);

    socket.onopen = () => {
      console.log("WebSocket connection established");
      setError(undefined);
    };

    socket.onmessage = (event) => {
      console.log("WebSocket message received:", event.data);
      const data = JSON.parse(event.data as string) as object;
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
      socket.close();
    };
  }, [requestId]);

  const handleWebSocketMessage = (data: object) => {
    console.log("Handling WebSocket message:", data);
    const message = z.union([statusMessage, resultMessage]).parse(data);
    if (message.type === "status") {
      setContent(message.consoleContent);
    } else if (message.type === "result") {
      handleResultMesage(message);
    }
    const validatedStatus = statusMessage.parse(data);
    console.log("Validated status:", validatedStatus);
    setContent(validatedStatus.consoleContent);
  };

  const executeQuery = (query: string) => {
    if (query && ws.current?.readyState === WebSocket.OPEN) {
      setIsLoading(true);
      setError(undefined);
      setResults(undefined);
      setUpdatedRows(undefined);
      sendMessage(executeStatementMessage, {
        type: "execute",
        statement: query,
      });
    }
  };

  const handleResultMesage = (message: z.infer<typeof resultMessage>) => {
    setResults(message.results);
    setIsLoading(false);
  };

  const sendMessage = <T extends z.ZodType>(schema: T, message: z.infer<T>) => {
    if (ws.current?.readyState === WebSocket.OPEN) {
      try {
        const validatedMessage = schema.parse(message) as z.infer<T>;
        ws.current.send(JSON.stringify(validatedMessage));
      } catch (error) {
        if (error instanceof z.ZodError) {
          console.error("Invalid message format:", error.errors);
          setError("Invalid message format");
        } else {
          console.error("Error sending message:", error);
          setError("Error sending message");
        }
      }
    } else {
      setError("WebSocket is not open");
    }
  };

  const updateContent = (content: string) => {
    sendMessage(updateContentMessage, { type: "update_content", content });
  };

  const debouncedUpdateContent = debounce((content: string) => {
    updateContent(content);
  }, 300) as (content: string) => void;

  return {
    error,
    executeQuery,
    updateContent: debouncedUpdateContent,
    isLoading,
    results,
    updatedRows,
  };
};

export default useLiveSession;
