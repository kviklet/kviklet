import { useFormContext } from "react-hook-form";
import {
  FormRow,
  FormSection,
  inputClassName,
} from "../../../../components/FormSection";
import { ConnectionFormInput } from "./connectionForm";

export default function KubernetesSection() {
  const {
    register,
    formState: { errors },
  } = useFormContext<ConnectionFormInput>();

  return (
    <FormSection
      title="Command execution"
      description="Kviklet runs commands as the user of its own pod, so that user needs permission to exec into the target pods."
    >
      <FormRow
        label="Initial wait (seconds)"
        htmlFor="kubernetesExecInitialWaitTimeoutSeconds"
        hint="How long Kviklet waits for a command's output before it responds."
        error={errors.kubernetesExecInitialWaitTimeoutSeconds?.message}
      >
        <input
          id="kubernetesExecInitialWaitTimeoutSeconds"
          type="number"
          min={1}
          className={inputClassName}
          data-testid="kubernetes-exec-initial-wait-timeout-seconds"
          {...register("kubernetesExecInitialWaitTimeoutSeconds")}
        />
      </FormRow>
      <FormRow
        label="Command timeout (minutes)"
        htmlFor="kubernetesExecTimeoutMinutes"
        hint="A command that is still running after this time is timed out."
        error={errors.kubernetesExecTimeoutMinutes?.message}
      >
        <input
          id="kubernetesExecTimeoutMinutes"
          type="number"
          min={1}
          className={inputClassName}
          data-testid="kubernetes-exec-timeout-minutes"
          {...register("kubernetesExecTimeoutMinutes")}
        />
      </FormRow>
    </FormSection>
  );
}
