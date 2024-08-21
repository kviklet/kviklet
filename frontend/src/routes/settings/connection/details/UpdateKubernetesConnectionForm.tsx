import { z } from "zod";
import { KubernetesConnectionResponse } from "../../../../api/DatasourceApi";
import InputField, { TextField } from "../../../../components/InputField";
import { Disclosure } from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import Button from "../../../../components/Button";
import { useConnectionForm } from "./ConnectionEditFormHook";

const kubernetesConnectionFormSchema = z
  .object({
    displayName: z.string().min(3),
    description: z.string(),
    reviewConfig: z.object({
      numTotalRequired: z.coerce.number(),
    }),
    maxExecutions: z.coerce.number().nullable(),
  })
  .transform((data) => ({ ...data, connectionType: "KUBERNETES" }));
type KubernetesConnectionForm = z.infer<typeof kubernetesConnectionFormSchema>;

interface UpdateFormProps {
  connection: KubernetesConnectionResponse;
  editConnection: (connection: KubernetesConnectionForm) => Promise<void>;
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
        numTotalRequired: connection.reviewConfig.numTotalRequired,
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
            label="Required reviews"
            id="numTotalRequired"
            placeholder="1"
            tooltip="The number of required approving reviews that's required before a request can be executed."
            type="number"
            {...register("reviewConfig.numTotalRequired")}
            error={errors.reviewConfig?.numTotalRequired?.message}
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
            type={isDirty ? "submit" : "disabled"}
            className="btn btn-primary mt-4"
          >
            Save
          </Button>
        </div>
      </div>
    </form>
  );
}
