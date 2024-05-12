import { useForm } from "react-hook-form";
import {
  KubernetesConnectionPayload,
  kubernetesConnectionPayloadSchema,
} from "../../../api/DatasourceApi";
import { zodResolver } from "@hookform/resolvers/zod";
import InputField from "../../../components/InputField";
import { useEffect } from "react";
import Button from "../../../components/Button";

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
            {...register("displayName")}
            error={errors.displayName?.message}
          />
          <InputField
            label="Description"
            id="description"
            placeholder="Provides prod read access with no required reviews"
            {...register("description")}
            error={errors.description?.message}
          />
          <InputField
            label="Connection ID"
            id="id"
            placeholder="datasource-id"
            {...register("id")}
            error={errors.id?.message}
          />
          <InputField
            label="Required reviews"
            id="numTotalRequired"
            placeholder="1"
            type="number"
            {...register("reviewConfig.numTotalRequired")}
            error={errors.reviewConfig?.numTotalRequired?.message}
          />
          <InputField
            id="maxExecutions"
            label="Max executions"
            placeholder="Max executions"
            type="number"
            {...register("maxExecutions")}
            error={errors.maxExecutions?.message}
          />
          <Button type="submit">Create Connection</Button>
        </div>
      </div>
    </form>
  );
}
