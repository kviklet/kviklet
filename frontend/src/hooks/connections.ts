import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  PatchConnectionPayload,
  PatchDatabaseConnectionPayload,
  addConnection,
  deleteConnection,
  getConnection,
  getConnections,
  patchConnection,
} from "../api/DatasourceApi";
import useNotification from "./useNotification";
import { isApiErrorResponse } from "../api/Errors";

const useConnections = () => {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const { addNotification } = useNotification();
  const [loading, setLoading] = useState<boolean>(true);
  useEffect(() => {
    async function request() {
      const response = await getConnections();
      if (isApiErrorResponse(response)) {
        addNotification({
          title: "Failed to load connections",
          text: response.message,
          type: "error",
        });
      } else {
        setConnections(response);
      }
      setLoading(false);
    }
    void request();
  }, []);

  const createConnection = async (connection: ConnectionPayload) => {
    const response = await addConnection(connection);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to create connection",
        text: response.message,
        type: "error",
      });
      return;
    } else {
      const newConnections = [...connections, response];
      setConnections(newConnections);
    }
  };

  const editConnection = async (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => {
    const response = await patchConnection(connection, connectionId);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to edit connection",
        text: response.message,
        type: "error",
      });
      return;
    } else {
      const newConnections = connections.map((connection) => {
        if (connection.id === response.id) {
          return response;
        }
        return connection;
      });
      setConnections(newConnections);
    }
  };

  return {
    loading,
    connections,
    createConnection,
    editConnection,
  };
};

const useConnection = (id: string) => {
  const [connection, setConnection] = useState<ConnectionResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);

  const { addNotification } = useNotification();

  async function request() {
    setLoading(true);
    const response = await getConnection(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to load connection",
        text: response.message,
        type: "error",
      });
    } else {
      setConnection(response);
    }
    setLoading(false);
  }

  useEffect(() => {
    void request();
  }, [id]);

  const editConnection = async (patchedConnection: PatchConnectionPayload) => {
    if (isDataSourceConnection(patchedConnection)) {
      if (patchedConnection.password === "") {
        patchedConnection.password = undefined;
      }
    }
    if (!connection?.id) {
      return;
    }
    setLoading(true);
    const response = await patchConnection(patchedConnection, connection.id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to edit connection",
        text: response.message,
        type: "error",
      });
    } else {
      setConnection(response);
    }
    setLoading(false);
  };

  const removeConnection = async () => {
    const response = await deleteConnection(id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to delete connection",
        text: response.message,
        type: "error",
      });
      return response;
    } else {
      addNotification({
        title: "Connection deleted",
        text: "The connection has been deleted",
        type: "info",
      });
      return null;
    }
  };

  return {
    loading,
    connection,
    editConnection,
    removeConnection,
  };
};

function isDataSourceConnection(
  connection: PatchConnectionPayload,
): connection is PatchDatabaseConnectionPayload {
  return connection.connectionType === "DATASOURCE";
}

export default useConnections;
export { useConnection };
