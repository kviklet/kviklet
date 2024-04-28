import { useEffect, useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  PatchConnectionPayload,
  addConnection,
  getConnections,
  patchConnection,
} from "../../../api/DatasourceApi";
import Button from "../../../components/Button";
import Modal from "../../../components/Modal";
import Spinner from "../../../components/Spinner";
import CreateKubernetesConnectionForm from "./KubernetesConnectionForm";
import SingleKubernetesConnectionSettings from "./SingleKubernetesSettings";
import SingleDatabaseConnectionSettings from "./SingleDatabaseSettings";
import NewDatabaseConnectionForm from "./NewDatabaseConnectionForm";

const useIdNamePair = () => {
  const [displayName, setDisplayName] = useState<string>("");
  const [id, setId] = useState<string>("");
  const [idManuallyChanged, setIdManuallyChanged] = useState<boolean>(false);

  const changeId = (id: string) => {
    setId(id);
    setIdManuallyChanged(true);
  };

  function formatId(input: string): string {
    const lowerCasedString = input.toLowerCase();
    return lowerCasedString.replace(/\s+/g, "-");
  }

  const changeDisplayName = (name: string) => {
    setDisplayName(name);
    if (!idManuallyChanged) {
      setId(formatId(name));
    }
  };

  return { displayName, id, changeId, changeDisplayName };
};

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

function ConnectionSettings(props: {
  connections: ConnectionResponse[];
  addConnectionHandler: () => void;
  addKubnernetesConnectionHandler: () => void;
  editConnectionHandler: (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => Promise<void>;
}) {
  return (
    <div className="flex flex-col max-h-[calc(100vh-theme(spacing.52))] w-full border-l dark:border-slate-700  dark:bg-slate-950">
      <div className="pl-8 text-lg">Connections</div>
      <div className="flex-grow overflow-hidden">
        <div className="pl-5 h-full flex flex-col justify-between">
          <div className="overflow-y-auto flex-grow">
            {props.connections.map((connection) =>
              connection._type === "DATASOURCE" ? (
                <SingleDatabaseConnectionSettings
                  key={connection.id} // Assuming each connection has a unique 'id'
                  connection={connection}
                  editConnectionHandler={props.editConnectionHandler}
                />
              ) : connection._type === "KUBERNETES" ? (
                <SingleKubernetesConnectionSettings
                  key={connection.id} // Assuming each connection has a unique 'id'
                  connection={connection}
                  editConnectionHandler={props.editConnectionHandler}
                />
              ) : null,
            )}
          </div>
          <Button
            className="ml-auto mx-2 my-1"
            onClick={props.addConnectionHandler}
          >
            Add connection
          </Button>
          <Button
            className="ml-auto mx-2 my-1"
            onClick={props.addKubnernetesConnectionHandler}
          >
            Add Kubernetes connection
          </Button>
        </div>
      </div>
    </div>
  );
}

const DatabaseSettings = () => {
  const { loading, connections, createConnection, editConnection } =
    useConnections();

  const [showAddConnectionModal, setShowAddConnectionModal] =
    useState<boolean>(false);

  const [
    showAddKubernetesConnectionModal,
    setShowAddKubernetesConnectionModal,
  ] = useState<boolean>(false);

  const handleCreateConnection = async (connection: ConnectionPayload) => {
    await createConnection(connection);
    setShowAddConnectionModal(false);
    setShowAddKubernetesConnectionModal(false);
  };

  return (
    <div className="h-full">
      {loading ? (
        <Spinner />
      ) : (
        <div className="">
          <div className="flex w-full dark:bg-slate-950 h-full">
            <ConnectionSettings
              connections={connections}
              addConnectionHandler={() => {
                setShowAddConnectionModal(true);
              }}
              editConnectionHandler={async (connectionId, connection) => {
                await editConnection(connectionId, connection);
              }}
              addKubnernetesConnectionHandler={() => {
                setShowAddKubernetesConnectionModal(true);
              }}
            />
          </div>
          {showAddConnectionModal && (
            <Modal setVisible={setShowAddConnectionModal}>
              <NewDatabaseConnectionForm
                handleCreateConnection={handleCreateConnection}
              />
            </Modal>
          )}
          {showAddKubernetesConnectionModal && (
            <Modal setVisible={setShowAddKubernetesConnectionModal}>
              {
                <CreateKubernetesConnectionForm
                  handleCreateConnection={handleCreateConnection}
                />
              }
            </Modal>
          )}
        </div>
      )}
    </div>
  );
};

export default DatabaseSettings;

export { useConnections, useIdNamePair };
