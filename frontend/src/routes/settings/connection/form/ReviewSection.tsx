import { useFormContext } from "react-hook-form";
import {
  FormRow,
  FormSection,
  inputClassName,
} from "../../../../components/FormSection";
import RoleRequirementsSection, {
  RoleRequirementField,
} from "../../../../components/RoleRequirementsSection";
import { useRoleRequirements } from "../../../../hooks/useRoleRequirements";
import { ConnectionFormInput } from "./connectionForm";

export default function ReviewSection(props: {
  initialRoleRequirements?: RoleRequirementField[];
}) {
  const {
    register,
    watch,
    setValue,
    getValues,
    formState: { errors },
  } = useFormContext<ConnectionFormInput>();
  const {
    roleRequirements,
    handleAppendRole,
    handleRemoveRole,
    handleUpdateRole,
    minRequired,
  } = useRoleRequirements(setValue, getValues, props.initialRoleRequirements);

  return (
    <FormSection
      title="Review"
      description="Who has to approve before a request on this connection can run."
    >
      <FormRow
        label="Required approvals"
        htmlFor="numTotalRequired"
        hint="0 lets requests run without a review."
        error={errors.reviewConfig?.numTotalRequired?.message}
      >
        <input
          id="numTotalRequired"
          type="number"
          min={minRequired}
          className={inputClassName}
          data-testid="connection-required-reviews"
          {...register("reviewConfig.numTotalRequired")}
        />
      </FormRow>
      <RoleRequirementsSection
        fields={roleRequirements}
        onAppend={handleAppendRole}
        onRemove={handleRemoveRole}
        onUpdate={handleUpdateRole}
        numTotalRequired={Number(watch("reviewConfig.numTotalRequired")) || 0}
      />
    </FormSection>
  );
}
