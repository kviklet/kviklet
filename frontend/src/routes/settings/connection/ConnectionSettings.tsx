import { useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { ConnectionResponse } from "../../../api/DatasourceApi";
import Button from "../../../components/Button";
import useConnections from "../../../hooks/connections";
import RequirePermission from "../../../components/RequirePermission";

const ConnectionSettings = () => {
  const { loading, connections } = useConnections();
  const navigate = useNavigate();

  const handleRowClick = (connection: ConnectionResponse) => {
    void navigate(`/settings/connections/${connection.id}`);
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
    void navigate(
      category
        ? `/settings/connections/new?category=${encodeURIComponent(category)}`
        : "/settings/connections/new",
    );
  };

  return (
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6 flex items-center justify-between">
        <h2 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">
          Connections
        </h2>
        {!loading && connections.length > 0 && (
          <RequirePermission permission="datasource_connection:create">
            <Button
              variant="primary"
              onClick={() => handleCreateClick(null)}
              dataTestId="add-connection-button"
            >
              Add connection
            </Button>
          </RequirePermission>
        )}
      </div>

      {loading ? (
        <div className="flex h-64 items-center justify-center">
          <div className="text-slate-500 dark:text-slate-400">Loading...</div>
        </div>
      ) : connections.length === 0 ? (
        <div className="flex h-64 flex-col items-center justify-center gap-4 rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900">
          <p className="text-slate-500 dark:text-slate-400">
            No connections found.
          </p>
          <RequirePermission permission="datasource_connection:create">
            <Button
              onClick={() => handleCreateClick(null)}
              dataTestId="add-connection-button"
            >
              Add connection
            </Button>
          </RequirePermission>
        </div>
      ) : (
        <div className="space-y-6" data-testid="connections-table">
          {groupedConnections.map((group) => (
            <div key={group.category ?? "uncategorized"}>
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-semibold text-slate-600 dark:text-slate-400">
                  {group.category ?? "Uncategorized"}
                </h3>
                <RequirePermission permission="datasource_connection:create">
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
                </RequirePermission>
              </div>
              <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow dark:border-slate-700 dark:bg-slate-900">
                {/* Fixed layout with shared column widths keeps the columns of all category tables aligned. */}
                <table className="w-full table-fixed">
                  <thead className="bg-slate-50 dark:bg-slate-800">
                    <tr>
                      <th className="w-1/3 px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                        Name
                      </th>
                      <th className="px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
                        Description
                      </th>
                      <th className="w-36 px-6 py-3 text-left text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-300">
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
                        <td
                          className="truncate px-6 py-4 text-sm text-slate-900 dark:text-slate-100"
                          title={connection.displayName}
                        >
                          {connection.displayName}
                        </td>
                        <td
                          className="truncate px-6 py-4 text-sm text-slate-900 dark:text-slate-100"
                          title={connection.description}
                        >
                          {connection.description}
                        </td>
                        <td className="truncate px-6 py-4 text-sm text-slate-600 dark:text-slate-400">
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
    </div>
  );
};

export default ConnectionSettings;

export { useConnections };
