import { useState, useCallback } from "react";
import { UseFormSetValue, UseFormGetValues } from "react-hook-form";
import { RoleRequirementField } from "../components/RoleRequirementsSection";

export function useRoleRequirements(
  setValue: UseFormSetValue<any>,
  getValues: UseFormGetValues<any>,
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
      setValue(
        "reviewConfig.roleRequirements" as "reviewConfig",
        reqs.length > 0 ? (reqs as never) : (undefined as never),
        { shouldDirty: true },
      );

      // Auto-bump numTotalRequired if it's BELOW the new minimum
      const newMin =
        reqs.length > 0 ? Math.max(...reqs.map((r) => r.numRequired)) : 0;
      const current = Number(getValues("reviewConfig.numTotalRequired"));
      if (current < newMin) {
        setValue("reviewConfig.numTotalRequired", newMin, {
          shouldDirty: true,
        });
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
