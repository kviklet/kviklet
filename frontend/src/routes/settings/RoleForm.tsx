import React from "react";
import {
  useForm,
  useFieldArray,
  ControllerRenderProps,
  Controller,
} from "react-hook-form";
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
import ConnectionSelector from "./ConnectionSelector";
import Button from "../../components/Button";

type ConnectionPolicyKey =
  | "execution_request_read"
  | "execution_request_write"
  | "execution_request_review";

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
  execution_request_read: {
    label: "Read",
    tooltip: "Allows seeing requests on this selector.",
  },
  execution_request_write: {
    label: "Write",
    tooltip:
      "Allows creating, commenting and executing (your own) requests on this selector.",
  },
  execution_request_review: {
    label: "Review",
    tooltip: "Allows reviewing/approving requests on this selector.",
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

  type ConnectionSelectorField = ControllerRenderProps<
    Role,
    `connectionPolicies.${number}.selector`
  >;

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
                        | "userPolicy.editSelf",
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
                    {...register(`rolePolicy.${field}` as "rolePolicy.read")}
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
                <div className="flex items-start justify-between">
                  <div className="basis-3/4">
                    <legend
                      className="flex items-center text-base leading-7 text-slate-700 dark:text-slate-100"
                      title="The selector used for this policy. The selector is used to match connectionIds to which this policy applies to. If you just want to match one specific connection, sepcify it's ID, but you can also use wildcards like 'my-connection-*' to match all connections starting with 'my-connection-'."
                    >
                      Selector
                      <QuestionMarkCircleIcon className="ml-1 h-4 w-4 text-slate-400" />
                    </legend>

                    <Controller
                      name={`connectionPolicies.${index}.selector`}
                      control={control}
                      render={({
                        field,
                      }: {
                        field: ConnectionSelectorField;
                      }) => (
                        <ConnectionSelector
                          value={field.value}
                          onChange={field.onChange}
                        />
                      )}
                    ></Controller>
                  </div>
                  <button
                    type="button"
                    onClick={() => remove(index)}
                    className="text-red-500"
                  >
                    <TrashIcon className="h-5 w-5" />
                  </button>
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
                  execution_request_read: false,
                  execution_request_write: false,
                  execution_request_review: false,
                })
              }
              className="mt-2 flex items-center text-indigo-500"
            >
              <PlusIcon className="mr-1 h-5 w-5" /> Add Connection Policy
            </button>
          </div>
        </>
      )}

      <Button type="submit">Submit</Button>
    </form>
  );
};

export default RoleForm;
