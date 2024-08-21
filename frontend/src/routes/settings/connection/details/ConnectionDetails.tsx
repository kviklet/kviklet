import { useNavigate, useParams } from "react-router-dom";
import { useConnection } from "../../../../hooks/connections";
import Spinner from "../../../../components/Spinner";
import UpdateDatasourceConnectionForm from "./UpdateDatasourceConnectionForm";
import UpdateKubernetesConnectionForm from "./UpdateKubernetesConnectionForm";
import Button from "../../../../components/Button";
import { useState } from "react";
import DeleteConfirm from "../../../../components/DeleteConfirm";
import Modal from "../../../../components/Modal";
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
    if (result === null) {
      setShowDeleteModal(false);
      navigate("/settings/connections");
    }
  };

  if (loading) {
    return <Spinner />;
  }

  if (!connection) {
    return <div>Connection not found</div>;
  }

  return (
    <div>
      <div className="flex w-full flex-col">
        <div className="flex w-full items-center justify-between">
          <div className="text-lg font-semibold dark:text-white">
            Connection Settings
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

        <div className="flex justify-end">
          <Button onClick={() => setShowDeleteModal(true)} type="danger">
            Delete
          </Button>
        </div>
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
