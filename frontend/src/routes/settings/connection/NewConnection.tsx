import { useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate, useSearchParams } from "react-router-dom";
import Breadcrumbs from "../../../components/Breadcrumbs";
import Button from "../../../components/Button";
import useConnections, { supportsIamAuth } from "../../../hooks/connections";
import ConnectionTypePicker from "./form/ConnectionTypePicker";
import GeneralSection from "./form/GeneralSection";
import DatabaseSection from "./form/DatabaseSection";
import KubernetesSection from "./form/KubernetesSection";
import ReviewSection from "./form/ReviewSection";
import LimitsSection from "./form/LimitsSection";
import RequestOptionsSection from "./form/RequestOptionsSection";
import TestConnection from "./form/TestConnection";
import {
  ConnectionFormValues,
  ConnectionKind,
  connectionFormSchema,
  defaultAdditionalOptions,
  defaultPorts,
  newConnectionDefaults,
  protocolOptions,
  toConnectionId,
  toCreatePayload,
} from "./form/connectionForm";

const connectionsPath = "/settings/connections";

export default function NewConnection() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { connections, createConnection, testConnection } = useConnections();
  // Set after a connection test when the user wants one connection per database.
  const [databases, setDatabases] = useState<string[] | null>(null);

  const form = useForm({
    resolver: zodResolver(connectionFormSchema),
    defaultValues: newConnectionDefaults(searchParams.get("category")),
  });
  const {
    handleSubmit,
    watch,
    setValue,
    formState: { dirtyFields, isSubmitting },
  } = form;

  const connectionType = watch("connectionType");
  const type = watch("type");
  const id = watch("id");
  const kind: ConnectionKind =
    connectionType === "KUBERNETES" ? "KUBERNETES" : type;
  const idTaken = connections.some((connection) => connection.id === id);

  const changeKind = (newKind: ConnectionKind) => {
    setDatabases(null);
    if (newKind === "KUBERNETES") {
      setValue("connectionType", "KUBERNETES");
      return;
    }
    setValue("connectionType", "DATASOURCE");
    setValue("type", newKind);
    setValue("protocol", protocolOptions[newKind][0]);
    // Follow the type's defaults until the user has entered their own value.
    if (!dirtyFields.port) {
      setValue("port", defaultPorts[newKind]);
    }
    if (!dirtyFields.additionalJDBCOptions) {
      setValue("additionalJDBCOptions", defaultAdditionalOptions[newKind]);
    }
    if (!supportsIamAuth(newKind)) {
      setValue("authenticationType", "USER_PASSWORD");
    }
  };

  const onSubmit = async (values: ConnectionFormValues) => {
    const payload = toCreatePayload(values);
    if (payload.connectionType === "DATASOURCE" && databases !== null) {
      for (const database of databases) {
        const created = await createConnection({
          ...payload,
          displayName: `${payload.displayName} - ${database}`,
          id: toConnectionId(`${payload.id}-${database}`),
          databaseName: database,
        });
        if (!created) {
          return;
        }
      }
      void navigate(connectionsPath);
      return;
    }
    // On failure the hook has already shown the error; stay here with the input intact.
    if (await createConnection(payload)) {
      void navigate(connectionsPath);
    }
  };

  const createsPerDatabase = connectionType === "DATASOURCE" && databases;

  return (
    <div className="container mx-auto px-4 py-8">
      <Breadcrumbs
        className="mb-6"
        items={[
          { label: "Connections", to: connectionsPath },
          { label: "New connection" },
        ]}
      />
      <FormProvider {...form}>
        <form
          noValidate
          onSubmit={(event) => void handleSubmit(onSubmit)(event)}
          className="flex max-w-3xl flex-col gap-6"
          data-testid="new-connection-form"
        >
          <ConnectionTypePicker value={kind} onChange={changeKind} />
          <GeneralSection showId idTaken={idTaken} />
          {connectionType === "DATASOURCE" ? (
            <DatabaseSection>
              <TestConnection
                testConnection={testConnection}
                databases={databases}
                onDatabasesChange={setDatabases}
              />
            </DatabaseSection>
          ) : (
            <KubernetesSection />
          )}
          <ReviewSection />
          <LimitsSection />
          <RequestOptionsSection />
          <div className="flex items-center justify-end gap-3">
            <Button onClick={() => void navigate(connectionsPath)}>
              Cancel
            </Button>
            <Button
              htmlType="submit"
              variant={
                isSubmitting ||
                idTaken ||
                (createsPerDatabase && databases.length === 0)
                  ? "disabled"
                  : "primary"
              }
              dataTestId="create-connection-button"
            >
              {createsPerDatabase && databases.length > 0
                ? `Create ${databases.length} connections`
                : "Create connection"}
            </Button>
          </div>
        </form>
      </FormProvider>
    </div>
  );
}
