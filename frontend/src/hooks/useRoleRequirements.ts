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
      (setValue as UseFormSetValue<FieldValues>)(
        "reviewConfig.roleRequirements",
        reqs.length > 0 ? reqs : undefined,
        { shouldDirty: true },
      );

      // Auto-bump numTotalRequired if it's BELOW the new minimum
      const newMin =
        reqs.length > 0 ? Math.max(...reqs.map((r) => r.numRequired)) : 0;
      const current = Number(
        (getValues as UseFormGetValues<FieldValues>)(
          "reviewConfig.numTotalRequired",
        ),
      );
      if (current < newMin) {
        (setValue as UseFormSetValue<FieldValues>)(
          "reviewConfig.numTotalRequired",
          newMin,
          {
            shouldDirty: true,
          },
        );
      }
      // NEVER auto-lower: if user manually set it higher, leave it alone
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
