import { useFormContext } from "react-hook-form";
import {
  FormRow,
  FormSection,
  inputClassName,
} from "../../../../components/FormSection";
import { ConnectionFormInput } from "./connectionForm";

export default function LimitsSection() {
  const {
    register,
    watch,
    formState: { errors },
  } = useFormContext<ConnectionFormInput>();
  const isDatabase = watch("connectionType") === "DATASOURCE";

  return (
    <FormSection title="Limits" description="Leave a field empty for no limit.">
      <FormRow
        label="Executions per request"
        htmlFor="maxExecutions"
        hint="How often an approved request can be executed."
        error={errors.maxExecutions?.message}
      >
        <input
          id="maxExecutions"
          type="number"
          min={1}
          className={inputClassName}
          placeholder="No limit"
          data-testid="connection-max-executions"
          {...register("maxExecutions")}
        />
      </FormRow>
      {isDatabase && watch("temporaryAccessEnabled") && (
        <FormRow
          label="Longest temporary access (minutes)"
          htmlFor="maxTemporaryAccessDuration"
          hint="The longest duration someone can request temporary access for."
          error={errors.maxTemporaryAccessDuration?.message}
        >
          <input
            id="maxTemporaryAccessDuration"
            type="number"
            min={1}
            className={inputClassName}
            placeholder="No limit"
            data-testid="connection-max-temporary-access-duration"
            {...register("maxTemporaryAccessDuration")}
          />
        </FormRow>
      )}
    </FormSection>
  );
}
