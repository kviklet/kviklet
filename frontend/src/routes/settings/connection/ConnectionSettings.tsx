import { useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ConnectionPayload,
  ConnectionResponse,
} from "../../../api/DatasourceApi";
import Button from "../../../components/Button";
import Modal from "../../../components/Modal";
import SettingsTable, { Column } from "../../../components/SettingsTable";
import CreateKubernetesConnectionForm from "./KubernetesConnectionForm";
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

const ConnectionSettings = () => {
  const { loading, connections, createConnection } = useConnections();
  const navigate = useNavigate();

  const [showAddConnectionModal, setShowAddConnectionModal] =
    useState<boolean>(false);
  const [connectionTypeChoice, setConnectionTypeChoice] = useState<
    "database" | "kubernetes" | null
  >(null);

  const handleCreateConnection = async (connection: ConnectionPayload) => {
    await createConnection(connection);
    setShowAddConnectionModal(false);
    setConnectionTypeChoice(null);
  };

  const handleRowClick = (connection: ConnectionResponse) => {
    navigate(`/settings/connections/${connection.id}`);
  };

  const columns: Column<ConnectionResponse>[] = [
    {
      header: "Name",
      accessor: "displayName",
    },
    {
      header: "Description",
      accessor: "description",
    },
    {
      header: "Type",
      render: (connection) => {
        if ("_type" in connection && connection._type === "KUBERNETES") {
          return (
            <span className="text-slate-600 dark:text-slate-400">
              Kubernetes
            </span>
          );
        }
        return (
          <span className="text-slate-600 dark:text-slate-400">Database</span>
        );
      },
    },
  ];

  const handleCreateClick = () => {
    setShowAddConnectionModal(true);
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <SettingsTable
        title="Connections"
        data={connections}
        columns={columns}
        keyExtractor={(connection) => connection.id}
        onRowClick={handleRowClick}
        onCreate={handleCreateClick}
        createButtonLabel="Add Connection"
        emptyMessage="No connections found. Add one to get started."
        loading={loading}
        testId="connections-table"
      />

      {showAddConnectionModal && !connectionTypeChoice && (
        <Modal setVisible={setShowAddConnectionModal}>
          <div className="rounded-md border bg-slate-50 p-6 dark:border-slate-700 dark:bg-slate-900">
            <h2 className="mb-4 text-lg font-semibold">
              Choose Connection Type
            </h2>
            <div className="flex flex-col gap-3">
              <Button
                onClick={() => setConnectionTypeChoice("database")}
                className="w-full"
                dataTestId="add-database-connection-button"
              >
                Add Database Connection
              </Button>
              <Button
                onClick={() => setConnectionTypeChoice("kubernetes")}
                className="w-full"
              >
                Add Kubernetes Connection
              </Button>
              <Button
                onClick={() => {
                  setShowAddConnectionModal(false);
                  setConnectionTypeChoice(null);
                }}
                className="w-full"
              >
                Cancel
              </Button>
            </div>
          </div>
        </Modal>
      )}

      {showAddConnectionModal && connectionTypeChoice === "database" && (
        <Modal
          setVisible={() => {
            setShowAddConnectionModal(false);
            setConnectionTypeChoice(null);
          }}
        >
          <DatabaseConnectionForm
            createConnection={handleCreateConnection}
            closeModal={() => {
              setShowAddConnectionModal(false);
              setConnectionTypeChoice(null);
            }}
          />
        </Modal>
      )}

      {showAddConnectionModal && connectionTypeChoice === "kubernetes" && (
        <Modal
          setVisible={() => {
            setShowAddConnectionModal(false);
            setConnectionTypeChoice(null);
          }}
        >
          <CreateKubernetesConnectionForm
            handleCreateConnection={handleCreateConnection}
          />
        </Modal>
      )}
    </div>
  );
};

export default ConnectionSettings;

export { useConnections, useIdNamePair };
