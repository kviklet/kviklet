import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import InputField from "../../../components/InputField";
import { useCallback, useEffect, useState } from "react";
import Button from "../../../components/Button";
import { z } from "zod";
import { useCategories } from "../../../hooks/connections";
import CategoryAutocomplete from "../../../components/CategoryAutocomplete";
import { QuestionMarkCircleIcon } from "@heroicons/react/20/solid";
import { roleRequirementSchema } from "../../../api/DatasourceApi";
import RoleRequirementsSection, {
  RoleRequirementField,
} from "../../../components/RoleRequirementsSection";

const kubernetesConnectionPayloadSchema = z.object({
  connectionType: z.literal("KUBERNETES").default("KUBERNETES"),
  displayName: z.coerce.string(),
  id: z.string(),
  description: z.string(),
  reviewConfig: z.object({
    numTotalRequired: z.coerce.number(),
    roleRequirements: z.array(roleRequirementSchema).optional(),
  }),
  maxExecutions: z.coerce.number().nullable(),
  storeResults: z.boolean().default(false),
  category: z.string().nullable().optional(),
});

type KubernetesConnectionPayload = z.infer<
  typeof kubernetesConnectionPayloadSchema
>;

export default function CreateKubernetesConnectionForm(props: {
  handleCreateConnection: (
    connection: KubernetesConnectionPayload,
  ) => Promise<void>;
  initialCategory?: string | null;
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

  const { categories } = useCategories();
  const [roleRequirements, setRoleRequirements] = useState<
    RoleRequirementField[]
  >([]);
  const watchDisplayName = watch("displayName");

  useEffect(() => {
    const lowerCasedString = watchDisplayName?.toLowerCase() || "";
    setValue("id", lowerCasedString.replace(/\s+/g, "-"));
  }, [watchDisplayName]);

  const updateRoleRequirementsFormValue = useCallback(
    (reqs: RoleRequirementField[]) => {
      setValue(
        "reviewConfig.roleRequirements" as "reviewConfig",
        reqs.length > 0 ? (reqs as never) : (undefined as never),
        { shouldDirty: true },
      );
    },
    [setValue],
  );

  const handleAppendRole = useCallback(
    (field: RoleRequirementField) => {
      const updated = [...roleRequirements, field];
      setRoleRequirements(updated);
      updateRoleRequirementsFormValue(updated);
    },
    [roleRequirements, updateRoleRequirementsFormValue],
  );

  const handleRemoveRole = useCallback(
    (index: number) => {
      const updated = roleRequirements.filter((_, i) => i !== index);
      setRoleRequirements(updated);
      updateRoleRequirementsFormValue(updated);
    },
    [roleRequirements, updateRoleRequirementsFormValue],
  );

  const handleUpdateRole = useCallback(
    (index: number, field: RoleRequirementField) => {
      const updated = roleRequirements.map((f, i) => (i === index ? field : f));
      setRoleRequirements(updated);
      updateRoleRequirementsFormValue(updated);
    },
    [roleRequirements, updateRoleRequirementsFormValue],
  );

  useEffect(() => {
    setValue("reviewConfig", { numTotalRequired: 1 });
    setValue("maxExecutions", 1);
    setValue("storeResults", false);
    if (props.initialCategory) {
      setValue("category", props.initialCategory);
    }
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
          <div className="flex w-full justify-between">
            <label
              htmlFor="category"
              className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
            >
              Category
            </label>
            <CategoryAutocomplete
              value={watch("category")}
              onChange={(val) => setValue("category", val)}
              availableCategories={categories}
              placeholder="Optional: dev, staging, prod..."
            />
          </div>
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
          <RoleRequirementsSection
            fields={roleRequirements}
            onAppend={handleAppendRole}
            onRemove={handleRemoveRole}
            onUpdate={handleUpdateRole}
            numTotalRequired={watch("reviewConfig.numTotalRequired") || 0}
          />
          <InputField
            id="maxExecutions"
            label="Max executions"
            placeholder="Max executions"
            type="number"
            {...register("maxExecutions")}
            error={errors.maxExecutions?.message}
          />
          <div className="flex w-full justify-between">
            <label
              htmlFor="storeResults"
              className="my-auto mr-auto flex items-center text-sm font-medium text-slate-700 dark:text-slate-200"
              title="When enabled, stores command output (up to 50KB) in the event history."
            >
              Store Command Output
              <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400"></QuestionMarkCircleIcon>
            </label>
            <input
              type="checkbox"
              className="my-auto h-4 w-4"
              {...register("storeResults")}
            />
          </div>
          <Button htmlType="submit" variant="primary">
            Create Connection
          </Button>
        </div>
      </div>
    </form>
  );
}
