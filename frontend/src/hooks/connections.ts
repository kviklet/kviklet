import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  PatchConnectionPayload,
  PatchDatabaseConnectionPayload,
  addConnection,
  getConnection,
  getConnections,
  patchConnection,
} from "../api/DatasourceApi";

const useConnections = () => {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [loading, setLoading] = useState<boolean>(true);
  useEffect(() => {
    async function request() {
      const response = await getConnections();
      setConnections(response);
      setLoading(false);
    }
    void request();
  }, []);

  const createConnection = async (connection: ConnectionPayload) => {
    const newConnection = await addConnection(connection);
    const newConnections = [...connections, newConnection];
    setConnections(newConnections);
  };

  const editConnection = async (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => {
    const updatedConnection = await patchConnection(connection, connectionId);
    const newConnections = connections.map((connection) => {
      if (connection.id === updatedConnection.id) {
        return updatedConnection;
      }
      return connection;
    });
    setConnections(newConnections);
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

  async function request() {
    setLoading(true);
    const connection = await getConnection(id);
    setConnection(connection);
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
    const updatedConnection = await patchConnection(
      patchedConnection,
      connection.id,
    );
    setConnection(updatedConnection);
    setLoading(false);
  };

  return {
    loading,
    connection,
    editConnection,
  };
};

function isDataSourceConnection(
  connection: PatchConnectionPayload,
): connection is PatchDatabaseConnectionPayload {
  return connection.connectionType === "DATASOURCE";
}

export default useConnections;
export { useConnection };
