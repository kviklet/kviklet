import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import InputField from "../../../components/InputField";
import { useEffect } from "react";
import Button from "../../../components/Button";
import { z } from "zod";
import { useCategories } from "../../../hooks/connections";
import CategoryAutocomplete from "../../../components/CategoryAutocomplete";
import {
  ChevronDownIcon,
  ChevronRightIcon,
  QuestionMarkCircleIcon,
} from "@heroicons/react/20/solid";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
} from "@headlessui/react";
import { roleRequirementSchema } from "../../../api/DatasourceApi";
import RoleRequirementsSection from "../../../components/RoleRequirementsSection";
import { useRoleRequirements } from "../../../hooks/useRoleRequirements";

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
  initialCategory?: string | null;
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
    watch,
    setValue,
    getValues,
  } = useForm<KubernetesConnectionPayload>({
    resolver: zodResolver(kubernetesConnectionPayloadSchema),
  });

  const { categories } = useCategories();
  const watchDisplayName = watch("displayName");

  const {
    roleRequirements,
    handleAppendRole,
    handleRemoveRole,
    handleUpdateRole,
    minRequired,
  } = useRoleRequirements(setValue, getValues);

  useEffect(() => {
    const lowerCasedString = watchDisplayName?.toLowerCase() || "";
    setValue("id", lowerCasedString.replace(/\s+/g, "-"));
  }, [watchDisplayName]);

  useEffect(() => {
    setValue("reviewConfig", { numTotalRequired: 1 });
    setValue("maxExecutions", 1);
    setValue("storeResults", false);
    if (props.initialCategory) {
      setValue("category", props.initialCategory);
    }
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
            data-testid="kubernetes-connection-id"
            {...register("id")}
            error={errors.id?.message}
          />
          <InputField
            label="Required reviews"
            id="numTotalRequired"
            placeholder="1"
            type="number"
            min={minRequired}
            data-testid="kubernetes-connection-required-reviews"
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
          <div className="w-full">
            <Disclosure defaultOpen={false}>
              {({ open }) => (
                <>
                  <DisclosureButton
                    className="py-2"
                    data-testid="advanced-options-button"
                  >
                    <div className="flex flex-row justify-between">
                      <div className="flex flex-row">
                        <div>Advanced Options</div>
                      </div>
                      <div className="flex flex-row">
                        {open ? (
                          <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                        ) : (
                          <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                        )}
                      </div>
                    </div>
                  </DisclosureButton>
                  <DisclosurePanel unmount={false}>
                    <div className="flex-col space-y-2">
                      <InputField
                        id="maxExecutions"
                        label="Max executions"
                        placeholder="Max executions"
                        type="number"
                        data-testid="kubernetes-connection-max-executions"
                        {...register("maxExecutions")}
                        error={errors.maxExecutions?.message}
                      />
                      <div className="flex w-full justify-between">
                        <label
                          htmlFor="storeResults"
                          className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
                          title="When enabled, stores command output (up to 50KB) in the event history."
                        >
                          Store Command Output
                          <QuestionMarkCircleIcon className="ml-1 inline h-4 w-4 align-text-bottom text-slate-400"></QuestionMarkCircleIcon>
                        </label>
                        <input
                          type="checkbox"
                          className="my-auto h-4 w-4"
                          {...register("storeResults")}
                        />
                      </div>
                      <InputField
                        label="Kubernetes exec initial wait timeout (seconds)"
                        id="kubernetesExecInitialWaitTimeoutSeconds"
                        placeholder="5"
                        tooltip="Maps to kubernetes.exec.initial-wait-timeout-seconds"
                        type="number"
                        data-testid="kubernetes-exec-initial-wait-timeout-seconds"
                        {...register("kubernetesExecInitialWaitTimeoutSeconds")}
                        error={
                          errors.kubernetesExecInitialWaitTimeoutSeconds
                            ?.message
                        }
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
                    </div>
                  </DisclosurePanel>
                </>
              )}
            </Disclosure>
          </div>
          <Button
            htmlType="submit"
            variant="primary"
            data-testid="create-kubernetes-connection-button"
          >
            Create Connection
          </Button>
        </div>
      </div>
    </form>
  );
}
