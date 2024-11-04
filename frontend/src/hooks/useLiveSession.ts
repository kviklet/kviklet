import { useEffect, useRef, useState } from "react";
import { websocketBaseUrl } from "../api/base";
import { ExecuteResponseResult } from "../api/ExecutionRequestApi";
import {
  executeStatementMessage,
  responseMessage,
  updateContentMessage,
} from "../api/LiveSessionApi";
import { z } from "zod";
import debounce from "lodash/debounce";
import useNotification from "./useNotification";

const useLiveSession = (
  requestId: string,
  setContent: (content: string) => void,
) => {
  const ws = useRef<WebSocket | null>(null);
  const [results, setResults] = useState<ExecuteResponseResult[] | undefined>(
    undefined,
  );
  const [isLoading, setIsLoading] = useState(false);
  const [updatedRows, setUpdatedRows] = useState<number | undefined>(undefined);

  const { addNotification } = useNotification();

  useEffect(() => {
    // Initialize WebSocket connection
    const socket = new WebSocket(`${websocketBaseUrl}/sql/${requestId}`);

    socket.onopen = () => {
      console.log("WebSocket connection established");
    };

    socket.onmessage = (event) => {
      console.log("WebSocket message received:", event.data);
      try {
        handleWebSocketMessage(JSON.parse(event.data as string));
      } catch (err) {
        console.error("Failed to parse WebSocket message:", err);
        addNotification({
          type: "error",
          title: "Websocket error",
          text: "Failed to parse server response",
        });
        setIsLoading(false);
      }
    };

    socket.onerror = (error) => {
      console.error("WebSocket error:", error);
      addNotification({
        type: "error",
        title: "Websocket error",
        text: "Connection error occurred. Please try again.",
      });
      setIsLoading(false);
    };

    socket.onclose = (event) => {
      console.log("WebSocket connection closed", event);
      if (!event.wasClean) {
        console.error("Connection lost unexpectedly. Please refresh the page.");
        addNotification({
          type: "error",
          title: "Websocket error",
          text: "Connection lost unexpectedly. Please refresh the page.",
        });
        setIsLoading(false);
      }
    };

    ws.current = socket;

    return () => {
      if (socket.readyState === WebSocket.OPEN) {
        socket.close();
      }
    };
  }, [requestId]);

  const handleWebSocketMessage = (data: unknown) => {
    try {
      const message = responseMessage.safeParse(data);

      if (!message.success) {
        console.error("Invalid message format:", message.error);
        addNotification({
          type: "error",
          title: "Websocket error",
          text: "Received invalid response from server",
        });
        setIsLoading(false);
        return;
      }

      switch (message.data.type) {
        case "status":
          setContent(message.data.consoleContent);
          break;
        case "result":
          setResults(message.data.results);
          setIsLoading(false);
          break;
        case "error":
          addNotification({
            type: "error",
            title: "Query error",
            text: message.data.error,
          });
          break;
      }
    } catch (err) {
      console.error("Error handling WebSocket message:", err);
      addNotification({
        type: "error",
        title: "Websocket error",
        text: "Failed to process server response",
      });
      setIsLoading(false);
    }
  };

  const executeQuery = (query: string) => {
    if (!query.trim()) {
      addNotification({
        type: "error",
        title: "Query error",
        text: "Query cannot be empty",
      });
      return;
    }

    if (!ws.current || ws.current.readyState !== WebSocket.OPEN) {
      addNotification({
        type: "error",
        title: "Connection error",
        text: "No connection to server. Please try again.",
      });
      return;
    }

    setIsLoading(true);
    setResults(undefined);
    setUpdatedRows(undefined);

    sendMessage(executeStatementMessage, {
      type: "execute",
      statement: query,
    });
  };

  const sendMessage = <T extends z.ZodType>(schema: T, message: z.infer<T>) => {
    if (!ws.current || ws.current.readyState !== WebSocket.OPEN) {
      console.error("Connection lost. Please refresh the page.");
      addNotification({
        type: "error",
        title: "Websocket error",
        text: "Connection lost. Please refresh the page.",
      });
      setIsLoading(false);
      return;
    }

    try {
      const validatedMessage = schema.parse(message) as z.infer<T>;
      ws.current.send(JSON.stringify(validatedMessage));
    } catch (error) {
      if (error instanceof z.ZodError) {
        console.error("Invalid message format:", error.errors);
        addNotification({
          type: "error",
          title: "Websocket error",
          text: "Failed to send message: Invalid format",
        });
      } else {
        console.error("Error sending message:", error);
        addNotification({
          type: "error",
          title: "Websocket error",
          text: "Failed to send message to server",
        });
      }
      setIsLoading(false);
    }
  };

  const updateContent = (content: string) => {
    sendMessage(updateContentMessage, { type: "update_content", content });
  };

  const debouncedUpdateContent = debounce((content: string) => {
    updateContent(content);
  }, 300) as (content: string) => void;

  return {
    executeQuery,
    updateContent: debouncedUpdateContent,
    isLoading,
    results,
    updatedRows,
  };
};

export default useLiveSession;
