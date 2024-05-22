import React from "react";
import { useForm, useFieldArray } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  PlusIcon,
  QuestionMarkCircleIcon,
  TrashIcon,
} from "@heroicons/react/20/solid";
import {
  ConnectionPolicy,
  Role,
  RolePolicy,
  RoleSchema,
  UserPolicySchema,
} from "../../hooks/roles";
import InputField from "../../components/InputField";

type ConnectionPolicyKey =
  | "create"
  | "edit"
  | "read"
  | "execution_request_get"
  | "execution_request_edit"
  | "execution_request_execute";

const userPolicyMetadata: Record<string, { label: string; tooltip: string }> = {
  read: {
    label: "Read Access",
    tooltip:
      "Allows listing other users. Required to see comments, etc. usually you want this permission on everyone.",
  },
  create: {
    label: "Create Users",
    tooltip:
      "Allows creating new users and deleting existing ones. Usually an admin permission, if you use SSO noone really needs this.",
  },
  editSelf: {
    label: "Edit Own Profile",
    tooltip:
      "Allows editing own profile. Necessary to change password, email and displayName. Usually you want this permission on everyone. SSO Users cannot change their email and can't set a password.",
  },
  editRoles: {
    label: "Manage Roles",
    tooltip:
      "Allows managing user roles. This is equivalent to admin permissions, since the user can edit their own roles and give themselves the admin role.",
  },
};

const rolePolicyMetadata: Record<string, { label: string; tooltip: string }> = {
  read: { label: "View Roles", tooltip: "Allows seeing a list of all roles." },
  edit: {
    label: "Modify Roles",
    tooltip:
      "Allows editing roles. Equivalent to admin permissions since the user could edit their own roles permissions.",
  },
};

const connectionPolicyMetadata: Record<
  ConnectionPolicyKey,
  { label: string; tooltip: string }
> = {
  read: {
    label: "Read Connections",
    tooltip: "Allows seeing this connection.",
  },
  create: {
    label: "Create Connections",
    tooltip: "Allows creating new connections.",
  },
  edit: {
    label: "Edit Connections",
    tooltip: "Allows editing connections.",
  },
  execution_request_get: {
    label: "Get Execution Requests",
    tooltip:
      "Allows seeing execution requests. This also allows Commenting and Reviewing.",
  },
  execution_request_edit: {
    label: "Edit Execution Requests",
    tooltip: "Allows Creating and editing execution requests.",
  },
  execution_request_execute: {
    label: "Execute Requests",
    tooltip: "Allows executing execution requests.",
  },
};

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
    watch,
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
      <legend className="text-base font-semibold leading-7 text-slate-700 dark:text-slate-100">
        General Settings
      </legend>
      <div className="mb-4">
        <InputField
          label="Role Name"
          stacked={true}
          error={errors.name?.message}
          {...register("name")}
        />
      </div>

      <div className="mb-4">
        <InputField
          label="Description"
          stacked={true}
          error={errors.description?.message}
          {...register("description")}
        />
      </div>

      <div className="mb-4">
        <label
          className="flex items-center text-sm font-medium text-slate-700 dark:text-slate-100"
          title="Gives the role all permissions on every resource."
        >
          Is Admin
          <QuestionMarkCircleIcon className="ml-1 h-4 text-slate-400" />
        </label>
        <input
          className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-600"
          {...register("isAdmin")}
          type="checkbox"
        />
      </div>

      {!watch("isAdmin") && (
        <>
          <fieldset className="mb-4">
            <legend className="text-base font-semibold leading-7 text-slate-700 dark:text-slate-100">
              User Permissions
            </legend>
            <div className="flex space-x-4">
              {Object.keys(UserPolicySchema.shape).map((field) => (
                <div key={field}>
                  <label
                    className="flex items-center text-sm font-medium text-slate-700 dark:text-slate-300"
                    title={userPolicyMetadata[field].tooltip}
                  >
                    {userPolicyMetadata[field].label}
                    <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400" />
                  </label>
                  <input
                    className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-600"
                    {...register(
                      `userPolicy.${field}` as
                        | "userPolicy.read"
                        | "userPolicy.create"
                        | "userPolicy.editSelf"
                        | "userPolicy.editRoles",
                    )}
                    type="checkbox"
                  />
                </div>
              ))}
            </div>
          </fieldset>

          <fieldset className="mb-4">
            <legend className="text-base font-semibold leading-7 text-slate-700 dark:text-slate-100">
              Role Permissions
            </legend>
            <div className="flex space-x-4">
              {Object.keys(RolePolicy.shape).map((field) => (
                <div key={field}>
                  <label
                    className="flex items-center text-sm font-medium text-slate-700 dark:text-slate-300"
                    title={rolePolicyMetadata[field].tooltip}
                  >
                    {rolePolicyMetadata[field].label}
                    <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400" />
                  </label>
                  <input
                    className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-600"
                    {...register(
                      `rolePolicy.${field}` as
                        | "rolePolicy.read"
                        | "rolePolicy.edit",
                    )}
                    type="checkbox"
                  />
                </div>
              ))}
            </div>
          </fieldset>

          <div>
            <h2 className="text-base font-semibold leading-7 text-slate-900 dark:text-slate-100">
              Connection Permissions
            </h2>
            {fields.map((field, index) => (
              <div
                key={field.id}
                className="mb-4 space-y-2 rounded-md border bg-white p-4 dark:border-slate-600 dark:bg-slate-950"
              >
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
                  <InputField
                    stacked={true}
                    tooltip={
                      "Specify either a concrete id or use a wildcard (*) to match all connections." +
                      "You can also combine prefixes with wildcards, e.g. 'k8s-*' to match all Kubernetes connections."
                    }
                    label="Selector"
                    error={
                      errors.connectionPolicies?.[index]?.selector?.message
                    }
                    {...register(
                      `connectionPolicies.${index}.selector` as const,
                    )}
                  />
                </div>
                <div className="grid grid-cols-3">
                  {Object.keys(ConnectionPolicy.shape)
                    .filter((key) => key !== "selector")
                    .map((key) => (
                      <div key={key}>
                        <label
                          className="flex items-center text-sm font-medium text-slate-700 dark:text-slate-300"
                          title={
                            connectionPolicyMetadata[key as ConnectionPolicyKey]
                              .tooltip
                          }
                        >
                          {
                            connectionPolicyMetadata[key as ConnectionPolicyKey]
                              .label
                          }
                          <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400" />
                        </label>
                        <input
                          className="h-4 w-4 rounded border-slate-300 bg-transparent text-indigo-600 focus:ring-indigo-600"
                          type="checkbox"
                          {...register(
                            `connectionPolicies.${index}.${
                              key as ConnectionPolicyKey
                            }`,
                          )}
                        />
                      </div>
                    ))}
                </div>
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
        </>
      )}

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
