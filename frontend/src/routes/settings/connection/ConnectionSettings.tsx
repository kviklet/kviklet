import { useState } from "react";
import {
  ConnectionPayload,
  ConnectionResponse,
  PatchConnectionPayload,
} from "../../../api/DatasourceApi";
import Button from "../../../components/Button";
import Modal from "../../../components/Modal";
import Spinner from "../../../components/Spinner";
import CreateKubernetesConnectionForm from "./KubernetesConnectionForm";
import ConnectionCard from "./ConnectionCard";
import DatabaseConnectionForm from "./DatabaseConnectionForm";
import useConnections from "../../../hooks/connections";

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

function ConnectionSettingsList(props: {
  connections: ConnectionResponse[];
  addConnectionHandler: () => void;
  addKubnernetesConnectionHandler: () => void;
  editConnectionHandler: (
    connectionId: string,
    connection: PatchConnectionPayload,
  ) => Promise<void>;
}) {
  return (
    <div className="flex max-h-[calc(100vh-theme(spacing.52))] w-full flex-col border-l pl-4 dark:border-slate-700  dark:bg-slate-950">
      <div className="text-lg">Connections</div>
      <div className="flex-grow overflow-hidden">
        <div className=" flex h-full flex-col justify-between">
          <div className="flex-grow overflow-y-auto">
            {props.connections.map((connection) => (
              <ConnectionCard key={connection.id} connection={connection} />
            ))}
          </div>
          <Button
            className="mx-2 my-1 ml-auto"
            onClick={props.addConnectionHandler}
            dataTestId="add-connection-button"
          >
            Add connection
          </Button>
          <Button
            className="mx-2 my-1 ml-auto"
            onClick={props.addKubnernetesConnectionHandler}
          >
            Add Kubernetes connection
          </Button>
        </div>
      </div>
    </div>
  );
}

const ConnectionSettings = () => {
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
          <div className="flex h-full w-full dark:bg-slate-950">
            <ConnectionSettingsList
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
              <DatabaseConnectionForm
                createConnection={createConnection}
                closeModal={() => setShowAddConnectionModal(false)}
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

export default ConnectionSettings;

export { useConnections, useIdNamePair };
