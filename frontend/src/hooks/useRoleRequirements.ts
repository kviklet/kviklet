import { useState, useCallback } from "react";
import {
  UseFormSetValue,
  UseFormGetValues,
  FieldValues,
} from "react-hook-form";
import { RoleRequirementField } from "../components/RoleRequirementsSection";

export function useRoleRequirements<T extends FieldValues>(
  setValue: UseFormSetValue<T>,
  getValues: UseFormGetValues<T>,
  initialRequirements: RoleRequirementField[] = [],
) {
  const [roleRequirements, setRoleRequirements] =
    useState<RoleRequirementField[]>(initialRequirements);

  const minRequired =
    roleRequirements.length > 0
      ? Math.max(...roleRequirements.map((r) => r.numRequired))
      : 0;

  const updateRoleRequirementsFormValue = useCallback(
    (reqs: RoleRequirementField[]) => {
      // Compute previous minimum BEFORE updating form (needed for auto-lower check)
      const oldReqs = (getValues as UseFormGetValues<FieldValues>)(
        "reviewConfig.roleRequirements",
      ) as RoleRequirementField[] | undefined;
      const oldMin =
        Array.isArray(oldReqs) && oldReqs.length > 0
          ? Math.max(...oldReqs.map((r) => r.numRequired))
          : 0;

      (setValue as UseFormSetValue<FieldValues>)(
        "reviewConfig.roleRequirements",
        reqs.length > 0 ? reqs : undefined,
        { shouldDirty: true },
      );

      const newMin =
        reqs.length > 0 ? Math.max(...reqs.map((r) => r.numRequired)) : 0;
      const current = Number(
        (getValues as UseFormGetValues<FieldValues>)(
          "reviewConfig.numTotalRequired",
        ),
      );

      // Auto-bump if below new minimum
      if (current < newMin) {
        (setValue as UseFormSetValue<FieldValues>)(
          "reviewConfig.numTotalRequired",
          newMin,
          { shouldDirty: true },
        );
      }
      // Auto-lower only if the total was exactly the previous minimum
      else if (current === oldMin && newMin < current) {
        (setValue as UseFormSetValue<FieldValues>)(
          "reviewConfig.numTotalRequired",
          newMin,
          { shouldDirty: true },
        );
      }
    },
    [setValue, getValues],
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

  return {
    roleRequirements,
    handleAppendRole,
    handleRemoveRole,
    handleUpdateRole,
    minRequired,
  };
}
