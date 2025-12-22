import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import InputField from "../../../components/InputField";
import { useEffect } from "react";
import Button from "../../../components/Button";
import { z } from "zod";

const kubernetesConnectionPayloadSchema = z.object({
  connectionType: z.literal("KUBERNETES").default("KUBERNETES"),
  displayName: z.coerce.string(),
  id: z.string(),
  description: z.string(),
  reviewConfig: z.object({
    numTotalRequired: z.coerce.number(),
  }),
  maxExecutions: z.coerce.number().nullable(),
  kubernetesExecInitialWaitTimeoutSeconds: z.coerce.number().default(5),
  kubernetesExecTimeoutMinutes: z.coerce.number().default(60),
});

type KubernetesConnectionPayload = z.infer<
  typeof kubernetesConnectionPayloadSchema
>;

export default function CreateKubernetesConnectionForm(props: {
  handleCreateConnection: (
    connection: KubernetesConnectionPayload,
  ) => Promise<void>;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
  } = useForm<KubernetesConnectionPayload>({
    resolver: zodResolver(kubernetesConnectionPayloadSchema),
  });

  const watchDisplayName = watch("displayName");

  useEffect(() => {
    const lowerCasedString = watchDisplayName?.toLowerCase() || "";
    setValue("id", lowerCasedString.replace(/\s+/g, "-"));
  }, [watchDisplayName]);

  useEffect(() => {
    setValue("reviewConfig", { numTotalRequired: 1 });
    setValue("maxExecutions", 1);
    setValue("kubernetesExecInitialWaitTimeoutSeconds", 5);
    setValue("kubernetesExecTimeoutMinutes", 60);
  }, []);

  const onSubmit = async (data: KubernetesConnectionPayload) => {
    await props.handleCreateConnection(data);
  };

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)}>
      <div className="w-2xl flex flex-col rounded-lg border border-slate-300 bg-slate-50 px-10 py-5 shadow dark:border-none dark:bg-slate-950">
        <h1 className="p-2 text-lg font-semibold">
          Add a new Kubernetes connection
        </h1>
        <div className="flex-col space-y-2">
          <InputField
            label="Connection name"
            id="displayName"
            placeholder="Connection name"
            data-testid="kubernetes-connection-name"
            {...register("displayName")}
            error={errors.displayName?.message}
          />
          <InputField
            label="Description"
            id="description"
            placeholder="Provides prod read access with no required reviews"
            data-testid="kubernetes-connection-description"
            {...register("description")}
            error={errors.description?.message}
          />
          <InputField
            label="Connection ID"
            id="id"
            placeholder="datasource-id"
            data-testid="kubernetes-connection-id"
            {...register("id")}
            error={errors.id?.message}
          />
          <InputField
            label="Required reviews"
            id="numTotalRequired"
            placeholder="1"
            type="number"
            data-testid="kubernetes-connection-required-reviews"
            {...register("reviewConfig.numTotalRequired")}
            error={errors.reviewConfig?.numTotalRequired?.message}
          />
          <InputField
            id="maxExecutions"
            label="Max executions"
            placeholder="Max executions"
            type="number"
            data-testid="kubernetes-connection-max-executions"
            {...register("maxExecutions")}
            error={errors.maxExecutions?.message}
          />
          <InputField
            label="Kubernetes exec initial wait timeout (seconds)"
            id="kubernetesExecInitialWaitTimeoutSeconds"
            placeholder="5"
            tooltip="Maps to kubernetes.exec.initial-wait-timeout-seconds"
            type="number"
            data-testid="kubernetes-exec-initial-wait-timeout-seconds"
            {...register("kubernetesExecInitialWaitTimeoutSeconds")}
            error={errors.kubernetesExecInitialWaitTimeoutSeconds?.message}
          />
          <InputField
            label="Kubernetes exec timeout (minutes)"
            id="kubernetesExecTimeoutMinutes"
            placeholder="60"
            tooltip="Maps to kubernetes.exec.timeout-minutes"
            type="number"
            data-testid="kubernetes-exec-timeout-minutes"
            {...register("kubernetesExecTimeoutMinutes")}
            error={errors.kubernetesExecTimeoutMinutes?.message}
          />
          <Button
            htmlType="submit"
            variant="primary"
            dataTestId="create-kubernetes-connection-button"
          >
            Create Connection
          </Button>
        </div>
      </div>
    </form>
  );
}
