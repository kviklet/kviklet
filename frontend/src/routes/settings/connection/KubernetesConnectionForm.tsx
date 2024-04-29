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

  const onSubmit = async (data: KubernetesConnectionPayload) => {
    await props.handleCreateConnection(data);
  };

  return (
    <form onSubmit={(event) => void handleSubmit(onSubmit)(event)}>
      <div className="flex flex-col w-2xl shadow px-10 py-5 bg-slate-50 border border-slate-300 dark:border-none dark:bg-slate-950 rounded-lg">
        <h1 className="text-lg font-semibold p-2">
          Add a new Kubernetes connection
        </h1>
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
        <Button type="submit" className="mt-4 btn btn-primary">
          Create Connection
        </Button>
      </div>
    </form>
  );
}
