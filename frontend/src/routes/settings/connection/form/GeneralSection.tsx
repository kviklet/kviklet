import { ChangeEvent } from "react";
import { useFormContext } from "react-hook-form";
import {
  FormRow,
  FormSection,
  inputClassName,
} from "../../../../components/FormSection";
import CategoryAutocomplete from "../../../../components/CategoryAutocomplete";
import { useCategories } from "../../../../hooks/connections";
import { ConnectionFormInput, toConnectionId } from "./connectionForm";

export default function GeneralSection(props: {
  /** The id can only be chosen while creating; afterwards it is fixed. */
  showId?: boolean;
  /** Set when another connection already uses the entered id. */
  idTaken?: boolean;
}) {
  const {
    register,
    watch,
    setValue,
    formState: { errors, dirtyFields },
  } = useFormContext<ConnectionFormInput>();
  const { categories } = useCategories();

  return (
    <FormSection title="General">
      <FormRow
        label="Name"
        htmlFor="displayName"
        error={errors.displayName?.message}
      >
        <input
          id="displayName"
          type="text"
          className={inputClassName}
          placeholder="orders-prod"
          data-testid="connection-name"
          {...register("displayName", {
            // While creating, the id follows the name until the user edits the id.
            onChange: (event: ChangeEvent<HTMLInputElement>) => {
              if (props.showId && !dirtyFields.id) {
                setValue("id", toConnectionId(event.target.value));
              }
            },
          })}
        />
      </FormRow>
      {props.showId && (
        <FormRow
          label="ID"
          htmlFor="id"
          hint="Role policies and the API refer to the connection by this ID. It can't be changed later."
          error={
            errors.id?.message ??
            (props.idTaken
              ? "Another connection already uses this ID"
              : undefined)
          }
        >
          <input
            id="id"
            type="text"
            className={`${inputClassName} font-mono`}
            placeholder="orders-prod"
            data-testid="connection-id"
            {...register("id")}
          />
        </FormRow>
      )}
      <FormRow
        label="Description"
        htmlFor="description"
        error={errors.description?.message}
      >
        <textarea
          id="description"
          rows={2}
          className={inputClassName}
          placeholder="Primary orders database. Writes need two approvals."
          data-testid="connection-description"
          {...register("description")}
        />
      </FormRow>
      <FormRow
        label="Category"
        hint="Optional. Groups connections in lists, for example dev, staging, prod."
      >
        <CategoryAutocomplete
          value={watch("category")}
          onChange={(value) =>
            setValue("category", value, { shouldDirty: true })
          }
          availableCategories={categories}
          placeholder="Select or type a category"
        />
      </FormRow>
    </FormSection>
  );
}
