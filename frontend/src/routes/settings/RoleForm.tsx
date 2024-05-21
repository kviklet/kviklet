import React from "react";
import { useForm, useFieldArray, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { PlusIcon, TrashIcon } from "@heroicons/react/20/solid";
import {
  ConnectionPolicy,
  Role,
  RolePolicy,
  RoleSchema,
  UserPolicySchema,
} from "../../hooks/roles";

const RoleForm = ({
  role,
  onSubmit,
}: {
  role: Role;
  onSubmit: (data: Role) => Promise<void>;
}) => {
  const {
    control,
    handleSubmit,
    register,
    formState: { errors },
  } = useForm<Role>({
    resolver: zodResolver(RoleSchema),
    defaultValues: role,
  });

  const { fields, append, remove } = useFieldArray({
    control,
    name: "connectionPolicies",
  });

  return (
    <form
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
      className="space-y-4"
    >
      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700">
          Role Name
        </label>
        <input
          {...register("name")}
          className="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        />
        {errors.name && (
          <span className="text-sm text-red-500">{errors.name.message}</span>
        )}
      </div>

      <div className="mb-4">
        <label className="block text-sm font-medium text-gray-700">
          Description
        </label>
        <input
          {...register("description")}
          className="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
        />
        {errors.description && (
          <span className="text-sm text-red-500">
            {errors.description.message}
          </span>
        )}
      </div>

      <fieldset className="mb-4">
        <legend className="text-lg font-medium text-gray-700">
          User Policy
        </legend>
        <div className="flex space-x-4">
          {Object.keys(UserPolicySchema.shape).map((field) => (
            <div key={field}>
              <label className="block text-sm font-medium text-gray-700">
                {field}
              </label>
              <Controller
                name={`userPolicy.${field}` as const}
                control={control}
                render={({ field }) => (
                  <input type="checkbox" {...field} checked={field.value} />
                )}
              />
            </div>
          ))}
        </div>
      </fieldset>

      <fieldset className="mb-4">
        <legend className="text-lg font-medium text-gray-700">
          Role Policy
        </legend>
        <div className="flex space-x-4">
          {Object.keys(RolePolicy.shape).map((field) => (
            <div key={field}>
              <label className="block text-sm font-medium text-gray-700">
                {field}
              </label>
              <Controller
                name={`rolePolicy.${field}` as const}
                control={control}
                render={({ field }) => (
                  <input type="checkbox" {...field} checked={field.value} />
                )}
              />
            </div>
          ))}
        </div>
      </fieldset>

      <div>
        <label className="block text-sm font-medium text-gray-700">
          Connection Policies
        </label>
        {fields.map((field, index) => (
          <div key={field.id} className="mb-4 space-y-2 rounded-md border p-4">
            <div className="flex items-center justify-between">
              <h3 className="text-lg font-medium">
                Connection Policy {index + 1}
              </h3>
              <button
                type="button"
                onClick={() => remove(index)}
                className="text-red-500"
              >
                <TrashIcon className="h-5 w-5" />
              </button>
            </div>
            <div>
              <label className="block text-sm font-medium text-gray-700">
                Selector
              </label>
              <input
                {...register(`connectionPolicies.${index}.selector` as const)}
                className="mt-1 block w-full rounded-md border border-gray-300 shadow-sm"
              />
              {errors.connectionPolicies?.[index]?.selector && (
                <span className="text-sm text-red-500">
                  {errors.connectionPolicies[index].selector?.message}
                </span>
              )}
            </div>
            {Object.keys(ConnectionPolicy.shape)
              .filter((key) => key !== "selector")
              .map((key) => (
                <div key={key}>
                  <label className="block text-sm font-medium text-gray-700">
                    {key}
                  </label>
                  <Controller
                    name={`connectionPolicies.${index}.${key}` as const}
                    control={control}
                    render={({ field }) => (
                      <input type="checkbox" {...field} checked={field.value} />
                    )}
                  />
                </div>
              ))}
          </div>
        ))}
        <button
          type="button"
          onClick={() =>
            append({
              selector: "",
              read: false,
              create: false,
              edit: false,
              execution_request_get: false,
              execution_request_edit: false,
              execution_request_execute: false,
            })
          }
          className="mt-2 flex items-center text-blue-500"
        >
          <PlusIcon className="mr-1 h-5 w-5" /> Add Connection Policy
        </button>
      </div>

      <button
        type="submit"
        className="rounded-md bg-blue-500 px-4 py-2 text-white"
      >
        Submit
      </button>
    </form>
  );
};

export default RoleForm;
