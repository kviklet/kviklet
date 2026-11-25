import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  DatabaseType,
  PatchConnectionPayload,
  addConnection,
  deleteConnection,
  getCategories,
  getConnection,
  getConnections,
  patchConnection,
  testConnection as testConnectionApi,
} from "../api/DatasourceApi";
import useNotification from "./useNotification";
import { isApiErrorResponse } from "../api/Errors";

// Utility function to normalize maxTemporaryAccessDuration field
// Converts 0 or falsy values to null to match backend expectations
const normalizeMaxTemporaryAccessDuration = <
  T extends ConnectionPayload | PatchConnectionPayload,
>(
  payload: T,
): T => {
  if (payload.connectionType === "DATASOURCE") {
    const duration = payload.maxTemporaryAccessDuration;
    if (!duration || duration === 0) {
      return {
        ...payload,
        maxTemporaryAccessDuration: null,
      };
    }
  }
  return payload;
};

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

  // Returns whether the connection was created, so callers keep their modal (and the
  // user's input) open on failure.
  const createConnection = async (connection: ConnectionPayload) => {
    const normalizedConnection =
      normalizeMaxTemporaryAccessDuration(connection);
    const response = await addConnection(normalizedConnection);

    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to create connection",
        text: response.message,
        type: "error",
      });
      return false;
    } else {
      const newConnections = [...connections, response];
      setConnections(newConnections);
      return true;
    }
  };

  const testConnection = async (connection: ConnectionPayload) => {
    const normalizedConnection =
      normalizeMaxTemporaryAccessDuration(connection);
    return await testConnectionApi(normalizedConnection);
  };

  return {
    loading,
    connections,
    createConnection,
    testConnection,
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

  // Returns whether the connection was saved, so the form keeps the user's input on failure.
  const editConnection = async (patchedConnection: PatchConnectionPayload) => {
    if (!connection?.id) {
      return false;
    }
    const normalizedConnection =
      normalizeMaxTemporaryAccessDuration(patchedConnection);
    // The PATCH endpoint can't distinguish null from "not provided", so removals are
    // signalled explicitly.
    normalizedConnection.clearCategory = !patchedConnection.category;
    if (normalizedConnection.connectionType === "DATASOURCE") {
      normalizedConnection.clearMaxTempDuration =
        !normalizedConnection.maxTemporaryAccessDuration;
    }
    const response = await patchConnection(normalizedConnection, connection.id);
    if (isApiErrorResponse(response)) {
      addNotification({
        title: "Failed to edit connection",
        text: response.message,
        type: "error",
      });
      return false;
    }
    setConnection(response);
    addNotification({
      title: "Connection saved",
      text: "The connection has been updated",
      type: "info",
    });
    return true;
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

const useCategories = () => {
  const [categories, setCategories] = useState<string[]>([]);
  const [loading, setLoading] = useState<boolean>(true);

  useEffect(() => {
    async function request() {
      const response = await getCategories();
      if (!isApiErrorResponse(response)) {
        setCategories(response);
      }
      setLoading(false);
    }
    void request();
  }, []);

  return { categories, loading };
};

function supportsIamAuth(type: DatabaseType): boolean {
  return [
    DatabaseType.POSTGRES,
    DatabaseType.MYSQL,
    DatabaseType.MARIADB,
  ].includes(type);
}

export default useConnections;
export { useConnection, useCategories, supportsIamAuth };
