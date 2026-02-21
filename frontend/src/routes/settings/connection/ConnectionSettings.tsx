import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  ConnectionPayload,
  ConnectionResponse,
} from "../../../api/DatasourceApi";
import Button from "../../../components/Button";
import Modal from "../../../components/Modal";
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
  const { loading, connections, createConnection, testConnection } =
    useConnections();
  const navigate = useNavigate();

  const [showAddConnectionModal, setShowAddConnectionModal] =
    useState<boolean>(false);
  const [connectionTypeChoice, setConnectionTypeChoice] = useState<
    "database" | "kubernetes" | null
  >(null);
  const [preselectedCategory, setPreselectedCategory] = useState<string | null>(
    null,
  );

  const handleCreateConnection = async (connection: ConnectionPayload) => {
    await createConnection(connection);
    setShowAddConnectionModal(false);
    setConnectionTypeChoice(null);
    setPreselectedCategory(null);
  };

  const handleRowClick = (connection: ConnectionResponse) => {
    navigate(`/settings/connections/${connection.id}`);
  };

  // Group connections by category: uncategorized first, then alphabetically by category
  const groupedConnections = useMemo(() => {
    const grouped = new Map<string | null, ConnectionResponse[]>();

    connections.forEach((conn) => {
      const category = conn.category ?? null;
      if (!grouped.has(category)) {
        grouped.set(category, []);
      }
      grouped.get(category)!.push(conn);
    });

    const groups: {
      category: string | null;
      connections: ConnectionResponse[];
    }[] = [];

    // Add uncategorized first
    if (grouped.has(null)) {
      groups.push({ category: null, connections: grouped.get(null)! });
      grouped.delete(null);
    }

    // Add categorized groups alphabetically
    const sortedCategories = Array.from(grouped.keys())
      .filter((k): k is string => k !== null)
      .sort((a, b) => a.localeCompare(b));

    sortedCategories.forEach((category) => {
      groups.push({ category, connections: grouped.get(category)! });
    });

    return groups;
  }, [connections]);

  const handleCreateClick = (category: string | null) => {
    setPreselectedCategory(category);
    setShowAddConnectionModal(true);
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
          Connections
        </h2>
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="text-slate-500 dark:text-slate-400">Loading...</div>
        </div>
      ) : connections.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center gap-4 rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <p className="text-slate-500 dark:text-slate-400">
            No connections found. Add one to get started.
          </p>
          <Button
            onClick={() => handleCreateClick(null)}
            dataTestId="connections-table-create-button"
          >
            Add Connection
          </Button>
        </div>
      ) : (
        <div className="space-y-6" data-testid="connections-table">
          {groupedConnections.map((group) => (
            <div key={group.category ?? "uncategorized"}>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-slate-600 dark:text-slate-400">
                  {group.category ?? "Uncategorized"}
                </h3>
                <button
                  onClick={() => handleCreateClick(group.category)}
                  className="flex h-6 w-6 items-center justify-center rounded text-slate-400 transition-colors hover:bg-slate-200 hover:text-slate-600 dark:hover:bg-slate-700 dark:hover:text-slate-300"
                  title={`Add connection to ${
                    group.category ?? "Uncategorized"
                  }`}
                  data-testid="connections-table-create-button"
                >
                  <span className="text-lg leading-none">+</span>
                </button>
              </div>
              <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow dark:border-slate-700 dark:bg-slate-900">
                <table className="min-w-full">
                  <thead className="bg-slate-50 dark:bg-slate-800">
                    <tr>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                        Name
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                        Description
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                        Type
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-700">
                    {group.connections.map((connection) => (
                      <tr
                        key={connection.id}
                        onClick={() => handleRowClick(connection)}
                        className="cursor-pointer transition-colors hover:bg-slate-50 dark:hover:bg-slate-800"
                        data-testid={`connections-table-row-${connection.id}`}
                      >
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-900 dark:text-slate-100">
                          {connection.displayName}
                        </td>
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-900 dark:text-slate-100">
                          {connection.description}
                        </td>
                        <td className="whitespace-nowrap px-6 py-4 text-sm text-slate-600 dark:text-slate-400">
                          {"_type" in connection &&
                          connection._type === "KUBERNETES"
                            ? "Kubernetes"
                            : "Database"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          ))}
        </div>
      )}

      {showAddConnectionModal && !connectionTypeChoice && (
        <Modal
          setVisible={() => {
            setShowAddConnectionModal(false);
            setPreselectedCategory(null);
          }}
        >
          <div className="rounded-lg border bg-slate-50 p-6 dark:border-slate-700 dark:bg-slate-950">
            <h2 className="mb-6 text-center text-lg font-semibold text-slate-900 dark:text-slate-100">
              Choose Connection Type
            </h2>
            <div className="flex gap-4">
              <button
                onClick={() => setConnectionTypeChoice("database")}
                className="flex flex-1 flex-col items-center rounded-lg border-2 border-slate-200 bg-white p-6 transition-all hover:border-indigo-500 hover:shadow-md dark:border-slate-600 dark:bg-slate-900 dark:hover:border-indigo-400 dark:hover:shadow-none"
                data-testid="add-database-connection-button"
              >
                <div className="mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-indigo-100 dark:bg-indigo-900">
                  <svg
                    className="h-6 w-6 text-indigo-600 dark:text-indigo-400"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4"
                    />
                  </svg>
                </div>
                <span className="text-base font-medium text-slate-900 dark:text-slate-100">
                  Database
                </span>
                <span className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                  PostgreSQL, MySQL, MongoDB...
                </span>
              </button>

              <button
                onClick={() => setConnectionTypeChoice("kubernetes")}
                className="flex flex-1 flex-col items-center rounded-lg border-2 border-slate-200 bg-white p-6 transition-all hover:border-indigo-500 hover:shadow-md dark:border-slate-600 dark:bg-slate-900 dark:hover:border-indigo-400 dark:hover:shadow-none"
                data-testid="add-kubernetes-connection-button"
              >
                <div className="relative mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-indigo-100 dark:bg-indigo-900">
                  <svg
                    className="h-6 w-6 text-indigo-600 dark:text-indigo-400"
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M5 12h14M5 12a2 2 0 01-2-2V6a2 2 0 012-2h14a2 2 0 012 2v4a2 2 0 01-2 2M5 12a2 2 0 00-2 2v4a2 2 0 002 2h14a2 2 0 002-2v-4a2 2 0 00-2-2m-2-4h.01M17 16h.01"
                    />
                  </svg>
                </div>
                <span className="text-base font-medium text-slate-900 dark:text-slate-100">
                  Kubernetes
                </span>
                <span className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                  Run commands in pods
                </span>
                <span className="mt-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900 dark:text-amber-300">
                  Beta
                </span>
              </button>
            </div>
          </div>
        </Modal>
      )}

      {showAddConnectionModal && connectionTypeChoice === "database" && (
        <Modal
          setVisible={() => {
            setShowAddConnectionModal(false);
            setConnectionTypeChoice(null);
            setPreselectedCategory(null);
          }}
        >
          <DatabaseConnectionForm
            createConnection={handleCreateConnection}
            testConnection={testConnection}
            closeModal={() => {
              setShowAddConnectionModal(false);
              setConnectionTypeChoice(null);
              setPreselectedCategory(null);
            }}
            initialCategory={preselectedCategory}
          />
        </Modal>
      )}

      {showAddConnectionModal && connectionTypeChoice === "kubernetes" && (
        <Modal
          setVisible={() => {
            setShowAddConnectionModal(false);
            setConnectionTypeChoice(null);
            setPreselectedCategory(null);
          }}
        >
          <CreateKubernetesConnectionForm
            handleCreateConnection={handleCreateConnection}
            initialCategory={preselectedCategory}
          />
        </Modal>
      )}
    </div>
  );
};

export default ConnectionSettings;

export { useConnections, useIdNamePair };
