import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  DatabaseType,
  PatchConnectionPayload,
  addConnection,
  deleteConnection,
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
        ...("clearMaxTempDuration" in payload && {
          clearMaxTempDuration: true,
        }),
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
      return;
    } else {
      const newConnections = [...connections, response];
      setConnections(newConnections);
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

  const editConnection = async (patchedConnection: PatchConnectionPayload) => {
    if (!connection?.id) {
      return;
    }
    setLoading(true);
    if (
      patchedConnection.connectionType === "DATASOURCE" &&
      patchedConnection.authenticationType === "USER_PASSWORD" &&
      patchedConnection.password === ""
    ) {
      // ensure that the password is not updated if the user didn't provide a new one
      patchedConnection.password = undefined;
    }
    const normalizedConnection =
      normalizeMaxTemporaryAccessDuration(patchedConnection);
    const response = await patchConnection(normalizedConnection, connection.id);
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

function supportsIamAuth(type: DatabaseType): boolean {
  return [DatabaseType.POSTGRES, DatabaseType.MYSQL].includes(type);
}

export default useConnections;
export { useConnection, supportsIamAuth };
