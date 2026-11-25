import { useEffect, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate, useParams } from "react-router-dom";
import {
  ConnectionResponse,
  PatchConnectionPayload,
} from "../../../../api/DatasourceApi";
import { hasPermission } from "../../../../api/Permissions";
import { useConnection } from "../../../../hooks/connections";
import Breadcrumbs from "../../../../components/Breadcrumbs";
import Button from "../../../../components/Button";
import DeleteConfirm from "../../../../components/DeleteConfirm";
import Modal from "../../../../components/Modal";
import ReadOnlyBadge from "../../../../components/ReadOnlyBadge";
import Spinner from "../../../../components/Spinner";
import GeneralSection from "../form/GeneralSection";
import DatabaseSection from "../form/DatabaseSection";
import KubernetesSection from "../form/KubernetesSection";
import ReviewSection from "../form/ReviewSection";
import LimitsSection from "../form/LimitsSection";
import RequestOptionsSection from "../form/RequestOptionsSection";
import {
  ConnectionFormValues,
  connectionToFormValues,
  editConnectionFormSchema,
  toPatchPayload,
} from "../form/connectionForm";

const connectionsPath = "/settings/connections";

function ConnectionEditForm(props: {
  connection: ConnectionResponse;
  canEdit: boolean;
  editConnection: (payload: PatchConnectionPayload) => Promise<boolean>;
}) {
  const { connection, canEdit } = props;
  const form = useForm({
    resolver: zodResolver(editConnectionFormSchema),
    defaultValues: connectionToFormValues(connection),
  });
  const {
    handleSubmit,
    reset,
    formState: { isDirty, isSubmitting },
  } = form;

  // A successful save hands back the stored connection; adopt it as the new clean state.
  useEffect(() => {
    reset(connectionToFormValues(connection));
  }, [connection, reset]);

  const onSubmit = async (values: ConnectionFormValues) => {
    await props.editConnection(toPatchPayload(values));
  };

  return (
    <FormProvider {...form}>
      <form
        noValidate
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="flex flex-col gap-6"
        data-testid="edit-connection-form"
      >
        <fieldset disabled={!canEdit} className="flex min-w-0 flex-col gap-6">
          <GeneralSection connectionId={connection.id} />
          {connection._type === "DATASOURCE" ? (
            <DatabaseSection
              showType
              passwordPlaceholder="Stored. Type to replace."
            />
          ) : (
            <KubernetesSection />
          )}
          <ReviewSection
            initialRoleRequirements={
              connection.reviewConfig.roleRequirements ?? []
            }
          />
          <LimitsSection />
          <RequestOptionsSection />
        </fieldset>
        {canEdit && (
          <div className="flex justify-end">
            <Button
              htmlType="submit"
              variant={isDirty && !isSubmitting ? "primary" : "disabled"}
              dataTestId="save-connection-button"
            >
              Save changes
            </Button>
          </div>
        )}
      </form>
    </FormProvider>
  );
}

export default function ConnectionDetails() {
  const { connectionId } = useParams() as { connectionId: string };
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
      void navigate(connectionsPath);
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
    <div className="container mx-auto px-4 py-8">
      <div className="mb-6 flex items-center gap-3">
        <Breadcrumbs
          items={[
            { label: "Connections", to: connectionsPath },
            { label: connection.displayName },
          ]}
        />
        {!canEdit && (
          <ReadOnlyBadge tooltip="You lack the permission to change this connection." />
        )}
      </div>
      <div className="flex max-w-3xl flex-col gap-6">
        <ConnectionEditForm
          connection={connection}
          canEdit={canEdit}
          editConnection={editConnection}
        />
        {canEdit && (
          <section className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-red-200 bg-white p-6 dark:border-red-900 dark:bg-slate-900">
            <div className="min-w-0 flex-1 basis-80">
              <h2 className="text-base font-semibold text-slate-900 dark:text-slate-50">
                Delete this connection
              </h2>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                This also removes its requests, comments and audit log entries.
              </p>
            </div>
            <Button
              onClick={() => setShowDeleteModal(true)}
              variant="danger"
              dataTestId="delete-connection-button"
            >
              Delete connection
            </Button>
          </section>
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
