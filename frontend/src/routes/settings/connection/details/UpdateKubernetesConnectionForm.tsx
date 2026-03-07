import { z } from "zod";
import {
  KubernetesConnectionResponse,
  PatchConnectionPayload,
  roleRequirementSchema,
} from "../../../../api/DatasourceApi";
import InputField, { TextField } from "../../../../components/InputField";
import { Disclosure } from "@headlessui/react";
import {
  ChevronDownIcon,
  ChevronRightIcon,
  QuestionMarkCircleIcon,
} from "@heroicons/react/20/solid";
import Button from "../../../../components/Button";
import { useConnectionForm } from "./ConnectionEditFormHook";
import { useCategories } from "../../../../hooks/connections";
import CategoryAutocomplete from "../../../../components/CategoryAutocomplete";
import RoleRequirementsSection from "../../../../components/RoleRequirementsSection";
import { useRoleRequirements } from "../../../../hooks/useRoleRequirements";

const kubernetesConnectionFormSchema = z.object({
  displayName: z.string().min(3),
  description: z.string(),
  reviewConfig: z.object({
    numTotalRequired: z.coerce.number(),
    roleRequirements: z.array(roleRequirementSchema).optional(),
  }),
  maxExecutions: z.coerce.number().nullable(),
  storeResults: z.boolean(),
  kubernetesExecInitialWaitTimeoutSeconds: z.coerce.number().min(1),
  kubernetesExecTimeoutMinutes: z.coerce.number().min(1),
  connectionType: z.literal("KUBERNETES").default("KUBERNETES"),
  category: z.string().nullable().optional(),
});

interface UpdateFormProps {
  connection: KubernetesConnectionResponse;
  editConnection: (connection: PatchConnectionPayload) => Promise<void>;
}

export default function UpdateKubernetesConnectionForm({
  connection,
  editConnection,
}: UpdateFormProps) {
  const { categories } = useCategories();

  const {
    register,
    formState: { errors, isDirty },
    watch,
    setValue,
    getValues,
    handleFormSubmit,
  } = useConnectionForm({
    initialValues: {
      displayName: connection.displayName,
      description: connection.description,
      reviewConfig: {
        numTotalRequired: connection.reviewConfig.numTotalRequired,
        roleRequirements: connection.reviewConfig.roleRequirements ?? undefined,
      },
      maxExecutions: connection.maxExecutions,
      storeResults: connection.storeResults,
      kubernetesExecInitialWaitTimeoutSeconds:
        connection.kubernetesExecInitialWaitTimeoutSeconds,
      kubernetesExecTimeoutMinutes: connection.kubernetesExecTimeoutMinutes,
      connectionType: "KUBERNETES",
      category: connection.category,
    },
    schema: kubernetesConnectionFormSchema,
    onSubmit: editConnection,
    connectionType: "KUBERNETES",
  });

  const {
    roleRequirements,
    handleAppendRole,
    handleRemoveRole,
    handleUpdateRole,
    minRequired,
  } = useRoleRequirements(
    setValue,
    getValues,
    connection.reviewConfig.roleRequirements ?? [],
  );

  return (
    <form
      onSubmit={(event) => {
        event.preventDefault();
        void handleFormSubmit();
      }}
    >
      <div className="flex w-full flex-col ">
        <div className="flex-col space-y-2">
          <InputField
            label="Connection name"
            id="displayName"
            placeholder="Connection name"
            data-testid="kubernetes-connection-name"
            {...register("displayName")}
            error={errors.displayName?.message}
          />
          <TextField
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
              onChange={(val) =>
                setValue("category", val, { shouldDirty: true })
              }
              availableCategories={categories}
              placeholder="Optional: dev, staging, prod..."
            />
          </div>
          <InputField
            label="Required reviews"
            id="numTotalRequired"
            placeholder="1"
            tooltip="The number of required approving reviews that's required before a request can be executed."
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
                  <Disclosure.Button
                    className="py-2"
                    data-testid="kubernetes-advanced-options-button"
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
                  </Disclosure.Button>
                  <Disclosure.Panel unmount={false}>
                    <div className="flex-col space-y-2">
                      <InputField
                        label="Max executions"
                        id="maxExecutions"
                        placeholder="Max executions"
                        tooltip="The maximum number of times each request can be executed after it has been approved, usually 1."
                        type="number"
                        data-testid="kubernetes-connection-max-executions"
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
                  </Disclosure.Panel>
                </>
              )}
            </Disclosure>
          </div>
          <Button
            htmlType="submit"
            variant={isDirty ? "primary" : "disabled"}
            className="btn btn-primary mt-4"
            dataTestId="save-kubernetes-connection-button"
          >
            Save
          </Button>
        </div>
      </div>
    </form>
  );
}
