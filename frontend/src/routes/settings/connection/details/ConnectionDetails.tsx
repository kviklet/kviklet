import { useNavigate, useParams } from "react-router-dom";
import { useConnection } from "../../../../hooks/connections";
import Breadcrumbs from "../../../../components/Breadcrumbs";
import Spinner from "../../../../components/Spinner";
import UpdateDatasourceConnectionForm from "./UpdateDatasourceConnectionForm";
import UpdateKubernetesConnectionForm from "./UpdateKubernetesConnectionForm";
import Button from "../../../../components/Button";
import { useState } from "react";
import DeleteConfirm from "../../../../components/DeleteConfirm";
import Modal from "../../../../components/Modal";
import { hasPermission } from "../../../../api/Permissions";
import ReadOnlyBadge from "../../../../components/ReadOnlyBadge";
interface ConnectionDetailsParams {
  connectionId: string;
}

export default function ConnectionDetails() {
  const params = useParams() as unknown as ConnectionDetailsParams;
  const connectionId = params.connectionId;

  const { loading, connection, editConnection, removeConnection } =
    useConnection(connectionId);
  const [showDeleteModal, setShowDeleteModal] = useState<boolean>(false);

  const navigate = useNavigate();

  const handleRemoveConfirm = async () => {
    const result = await removeConnection();
    // Close the confirm either way — on failure the error toast already explains
    // what happened, keeping the modal open just strands the user.
    setShowDeleteModal(false);
    if (result === null) {
      void navigate("/settings/connections");
    }
  };

  if (loading) {
    return <Spinner size="lg" page />;
  }

  if (!connection) {
    return <div>Connection not found</div>;
  }

  const canEdit = hasPermission(
    connection.permissions,
    "datasource_connection:edit",
  );

  return (
    <div>
      <div className="flex w-full flex-col">
        <Breadcrumbs
          className="mb-2"
          items={[
            { label: "Connections", to: "/settings/connections" },
            { label: connection.displayName },
          ]}
        />
        <div className="flex w-full items-center justify-between">
          <div className="flex items-center gap-2">
            <div className="text-lg font-semibold dark:text-white">
              Connection Settings
            </div>
            {!canEdit && (
              <ReadOnlyBadge tooltip="You lack the permission to change this connection." />
            )}
          </div>
        </div>
        {connection._type === "DATASOURCE" && (
          <UpdateDatasourceConnectionForm
            connection={connection}
            editConnection={editConnection}
          />
        )}
        {connection._type === "KUBERNETES" && (
          <UpdateKubernetesConnectionForm
            connection={connection}
            editConnection={editConnection}
          />
        )}

        {canEdit && (
          <div className="flex justify-end">
            <Button onClick={() => setShowDeleteModal(true)} variant="danger">
              Delete
            </Button>
          </div>
        )}
      </div>
      {showDeleteModal && (
        <Modal setVisible={setShowDeleteModal}>
          <DeleteConfirm
            title="Delete connection"
            message="Are you sure you want to delete this connection? This will remove all associated requests, comments, and auditlog entries."
            onConfirm={handleRemoveConfirm}
            onCancel={() => setShowDeleteModal(false)}
          />
        </Modal>
      )}
    </div>
  );
}
