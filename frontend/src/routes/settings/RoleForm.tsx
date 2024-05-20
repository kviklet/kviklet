import React from "react";
import { useForm, useFieldArray, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { PlusIcon, TrashIcon } from "@heroicons/react/20/solid";
import { z } from "zod";


const UserPolicySchema = z.object({
  read: z.boolean(),
  create: z.boolean(),
  editSelf: z.boolean(),
  delete: z.boolean(),
});

const RolePolicy = z.object({
  read: z.boolean(),
  edit: z.boolean(),
});

const ConnectionPolicy = z.object({
  selector: z.string(),
  read: z.boolean(),
  create: z.boolean(),
  edit: z.boolean(),
  execution_request_get: z.boolean(),
  execution_request_edit: z.boolean(),
  execution_request_execute: z.boolean(),
});

const RoleSchema = z.object({
  name: z.string(),
  description: z.string().nullable(),
  userPolicy: UserPolicySchema,
  rolePolicy: RolePolicy,
  connectionPolicies: z.array(ConnectionPolicy),
});

type Role = z.infer<typeof RoleSchema>;

const RoleForm = () => {
  const {
    control,
    handleSubmit,
    register,
    formState: { errors },
  } = useForm<Role>({
    resolver: zodResolver(RoleSchema),
    defaultValues: {
      name: "",
      description: null,
      userPolicy: {
        read: false,
        create: false,
        editSelf: false,
        delete: false,
      },
      rolePolicy: {
        read: false,
        edit: false,
      },
      connectionPolicies: [
        {
          selector: "",
          read: false,
          create: false,
          edit: false,
          execution_request_get: false,
          execution_request_edit: false,
          execution_request_execute: false,
        },
      ],
    },
  });

  const { fields, append, remove } = useFieldArray({
    control,
    name: "connectionPolicies",
  });

  const onSubmit = (data: Role) => {
    console.log(data);
  };

  return (
    <form onSubmit={void handleSubmit(onSubmit)} className="space-y-4">
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
                render={({ field }) => <input type="checkbox" {...field} />}
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
                render={({ field }) => <input type="checkbox" {...field} />}
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
                    render={({ field }) => <input type="checkbox" {...field} />}
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
