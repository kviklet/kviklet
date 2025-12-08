import { z } from "zod";
import {
  KubernetesConnectionResponse,
  PatchConnectionPayload,
} from "../../../../api/DatasourceApi";
import InputField, { TextField } from "../../../../components/InputField";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import Button from "../../../../components/Button";
import { useConnectionForm } from "./ConnectionEditFormHook";

const reviewGroupSchema = z.object({
  roleId: z.string(),
  numRequired: z.coerce.number(),
});

const reviewConfigSchema = z.object({
  groupConfigs: z.array(reviewGroupSchema).min(1),
});

const kubernetesConnectionFormSchema = z.object({
  displayName: z.string().min(3),
  description: z.string(),
  reviewConfig: reviewConfigSchema,
  maxExecutions: z.coerce.number().nullable(),
  connectionType: z.literal("KUBERNETES").default("KUBERNETES"),
});

interface UpdateFormProps {
  connection: KubernetesConnectionResponse;
  editConnection: (connection: PatchConnectionPayload) => Promise<void>;
}

export default function UpdateKubernetesConnectionForm({
  connection,
  editConnection,
}: UpdateFormProps) {
  const {
    register,
    formState: { errors, isDirty },
    handleFormSubmit,
  } = useConnectionForm({
    initialValues: {
      displayName: connection.displayName,
      description: connection.description,
      reviewConfig: {
        groupConfigs: connection.reviewConfig.groupConfigs.map((group) => ({
          roleId: group.roleId,
          numRequired: group.numRequired,
        })),
      },
      maxExecutions: connection.maxExecutions,
      connectionType: "KUBERNETES",
    },
    schema: kubernetesConnectionFormSchema,
    onSubmit: editConnection,
    connectionType: "KUBERNETES",
  });

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
            {...register("displayName")}
            error={errors.displayName?.message}
          />
          <TextField
            label="Description"
            id="description"
            placeholder="Provides prod read access with no required reviews"
            {...register("description")}
            error={errors.description?.message}
          />
          <InputField
            label="Required reviews (wildcard only)"
            id="numRequired"
            placeholder="1"
            tooltip="Number of approvals required from any role before a request can be executed."
            type="number"
            {...register("reviewConfig.groupConfigs.0.numRequired")}
            error={
              Array.isArray(errors.reviewConfig?.groupConfigs)
                ? errors.reviewConfig?.groupConfigs?.[0]?.numRequired?.message
                : undefined
            }
          />
          <div className="w-full">
            <Disclosure defaultOpen={false}>
              {({ open }) => (
                <>
                  <Disclosure.Button className="py-2">
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
                        {...register("maxExecutions")}
                        error={errors.maxExecutions?.message}
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
          >
            Save
          </Button>
        </div>
      </div>
    </form>
  );
}
