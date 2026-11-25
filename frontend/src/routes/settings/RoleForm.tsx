import { useEffect } from "react";
import {
  Controller,
  FormProvider,
  useFieldArray,
  useForm,
  useFormContext,
} from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { PlusIcon, XMarkIcon } from "@heroicons/react/20/solid";
import { Role, RoleSchema, newConnectionPolicy } from "../../hooks/roles";
import useConnections from "../../hooks/connections";
import Button from "../../components/Button";
import {
  CheckboxRow,
  FormRow,
  FormSection,
  checkboxClassName,
  inputClassName,
} from "../../components/FormSection";
import ConnectionSelector from "./ConnectionSelector";

type GlobalPermission =
  | "isAdmin"
  | "userPolicy.read"
  | "userPolicy.create"
  | "userPolicy.editSelf"
  | "rolePolicy.read";

const globalPermissions: {
  group: string;
  items: {
    name: GlobalPermission;
    label: string;
    description: string;
    testId: string;
  }[];
}[] = [
  {
    group: "Users",
    items: [
      {
        name: "userPolicy.read",
        label: "View users",
        description:
          "See the list of users. Needed to see who wrote a comment or review, so most roles should have it.",
        testId: "role-user-policy-read",
      },
      {
        name: "userPolicy.editSelf",
        label: "Edit own profile",
        description:
          "Change their own name, email and password. Users who sign in through SSO can't change their email or set a password.",
        testId: "role-user-policy-editSelf",
      },
      {
        name: "userPolicy.create",
        label: "Create users",
        description:
          "Add and remove users. Rarely needed when users sign in through SSO.",
        testId: "role-user-policy-create",
      },
    ],
  },
  {
    group: "Roles",
    items: [
      {
        name: "rolePolicy.read",
        label: "View roles",
        description: "See the list of roles and what each one allows.",
        testId: "role-role-policy-read",
      },
    ],
  },
];

const connectionPermissions = [
  {
    key: "execution_request_read",
    label: "Read",
    description: "see the requests on these connections",
  },
  {
    key: "execution_request_write",
    label: "Write",
    description: "create, comment on and execute their own requests",
  },
  {
    key: "execution_request_review",
    label: "Review",
    description: "approve or reject requests",
  },
] as const;

// Controlled instead of register(): the permission checkboxes disappear while the role is
// an admin, and react-hook-form resets a registered checkbox when it mounts again.
function PermissionCheckbox(props: {
  name: GlobalPermission;
  label: string;
  description: string;
  testId: string;
}) {
  const { watch, setValue } = useFormContext<Role>();
  return (
    <CheckboxRow
      id={props.name}
      label={props.label}
      description={props.description}
    >
      <input
        id={props.name}
        type="checkbox"
        className={checkboxClassName}
        data-testid={props.testId}
        checked={watch(props.name)}
        onChange={(event) =>
          setValue(props.name, event.target.checked, { shouldDirty: true })
        }
      />
    </CheckboxRow>
  );
}

function ConnectionAccess() {
  const {
    control,
    watch,
    setValue,
    formState: { errors },
  } = useFormContext<Role>();
  const { fields, append, remove } = useFieldArray({
    control,
    name: "connectionPolicies",
  });
  const { connections } = useConnections();
  const policies = watch("connectionPolicies");

  return (
    <FormSection
      title="Connection access"
      description={
        <>
          Each rule gives access to the connections whose ID matches its
          pattern. <code className="font-mono">*</code> stands for any text, so{" "}
          <code className="font-mono">orders-*</code> covers every connection
          whose ID starts with <code className="font-mono">orders-</code>, and a
          single <code className="font-mono">*</code> covers all connections.
        </>
      }
      testId="role-connection-access"
    >
      {fields.length > 0 && (
        <div className="hidden grid-cols-[minmax(0,1fr)_4rem_4rem_4rem_2rem] gap-3 text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400 sm:grid">
          <span>Connections</span>
          {connectionPermissions.map((permission) => (
            <span key={permission.key} className="text-center">
              {permission.label}
            </span>
          ))}
          <span />
        </div>
      )}
      {fields.map((field, index) => {
        const policy = policies[index];
        // Writing and reviewing include seeing the requests.
        const readImplied =
          policy?.execution_request_write || policy?.execution_request_review;
        return (
          <div
            key={field.id}
            className="grid grid-cols-[repeat(3,auto)_1fr] items-start gap-3 border-t border-slate-200 pt-3 dark:border-slate-700 sm:grid-cols-[minmax(0,1fr)_4rem_4rem_4rem_2rem]"
            data-testid={`role-connection-policy-${index}`}
          >
            <div className="col-span-full sm:col-span-1">
              <Controller
                name={`connectionPolicies.${index}.selector`}
                control={control}
                render={({ field: selector }) => (
                  <ConnectionSelector
                    value={selector.value}
                    onChange={selector.onChange}
                    connections={connections}
                    error={
                      errors.connectionPolicies?.[index]?.selector?.message
                    }
                  />
                )}
              />
            </div>
            {connectionPermissions.map((permission) => {
              const implied =
                permission.key === "execution_request_read" && readImplied;
              return (
                <label
                  key={permission.key}
                  className="flex items-center gap-2 text-sm text-slate-700 dark:text-slate-200 sm:justify-center sm:pt-2"
                >
                  <input
                    type="checkbox"
                    className={checkboxClassName.replace("mt-0.5 ", "")}
                    data-testid={`role-connection-policy-${index}-${permission.key}`}
                    checked={implied || (policy?.[permission.key] ?? false)}
                    disabled={implied}
                    onChange={(event) =>
                      setValue(
                        `connectionPolicies.${index}.${permission.key}`,
                        event.target.checked,
                        { shouldDirty: true },
                      )
                    }
                  />
                  <span className="sm:sr-only">{permission.label}</span>
                </label>
              );
            })}
            <button
              type="button"
              onClick={() => remove(index)}
              aria-label="Remove rule"
              data-testid={`role-connection-policy-${index}-remove`}
              className="justify-self-end rounded p-1.5 text-slate-400 transition-colors disabled:cursor-not-allowed disabled:opacity-50 hover:text-red-600 dark:hover:text-red-400"
            >
              <XMarkIcon className="h-5 w-5" aria-hidden="true" />
            </button>
          </div>
        );
      })}
      {fields.length === 0 && (
        <p className="text-sm text-slate-500 dark:text-slate-400">
          No rules yet, so this role gives access to no connection.
        </p>
      )}
      <div>
        <button
          type="button"
          data-testid="role-add-connection-policy-button"
          onClick={() => append(newConnectionPolicy(""))}
          className="flex items-center gap-1 rounded-md px-2 py-1.5 text-sm font-medium text-indigo-700 transition-colors disabled:cursor-not-allowed disabled:opacity-50 hover:bg-indigo-50 dark:text-indigo-300 dark:hover:bg-indigo-950"
        >
          <PlusIcon className="h-4 w-4" aria-hidden="true" />
          Add rule
        </button>
      </div>
      <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 border-t border-slate-200 pt-3 text-xs text-slate-500 dark:border-slate-700 dark:text-slate-400">
        {connectionPermissions.map((permission) => (
          <div key={permission.key} className="contents">
            <dt className="font-medium text-slate-700 dark:text-slate-200">
              {permission.label}
            </dt>
            <dd>{permission.description}</dd>
          </div>
        ))}
      </dl>
    </FormSection>
  );
}

const RoleForm = ({
  role,
  onSubmit,
  readOnly = false,
  submitLabel = "Save changes",
}: {
  role: Role;
  onSubmit: (data: Role) => Promise<void>;
  /** Renders the form as a pure viewer: all inputs disabled, no submit button. */
  readOnly?: boolean;
  submitLabel?: string;
}) => {
  const form = useForm<Role>({
    resolver: zodResolver(RoleSchema),
    defaultValues: role,
  });
  const {
    handleSubmit,
    register,
    reset,
    watch,
    formState: { errors, isDirty, isSubmitting },
  } = form;

  // A saved role comes back from the server; adopt it as the new clean state.
  useEffect(() => {
    reset(role);
  }, [role, reset]);

  const isAdmin = watch("isAdmin");
  // A new role starts out clean but still has to be creatable.
  const canSubmit = (isDirty || role.id === "") && !isSubmitting;

  return (
    <FormProvider {...form}>
      <form
        noValidate
        onSubmit={(event) => void handleSubmit(onSubmit)(event)}
        className="flex flex-col gap-6"
        data-testid="role-form"
      >
        <fieldset disabled={readOnly} className="flex min-w-0 flex-col gap-6">
          <FormSection title="General">
            <FormRow label="Name" htmlFor="name" error={errors.name?.message}>
              <input
                id="name"
                type="text"
                className={inputClassName}
                placeholder="Developers"
                {...register("name")}
              />
            </FormRow>
            <FormRow
              label="Description"
              htmlFor="description"
              error={errors.description?.message}
            >
              <input
                id="description"
                type="text"
                className={inputClassName}
                placeholder="Can request access to the staging databases"
                {...register("description")}
              />
            </FormRow>
          </FormSection>

          <FormSection
            title="Permissions"
            description="What members of this role can do outside of individual connections."
          >
            <PermissionCheckbox
              name="isAdmin"
              label="Administrator"
              description="Full access to everything: all connections and requests, users, roles and settings."
              testId="role-is-admin"
            />
            {!isAdmin &&
              globalPermissions.map((group) => (
                <div
                  key={group.group}
                  className="flex flex-col gap-3 border-t border-slate-200 pt-3 dark:border-slate-700"
                >
                  <h3 className="text-xs font-medium uppercase tracking-wider text-slate-500 dark:text-slate-400">
                    {group.group}
                  </h3>
                  {group.items.map((item) => (
                    <PermissionCheckbox key={item.name} {...item} />
                  ))}
                </div>
              ))}
          </FormSection>

          {!isAdmin && <ConnectionAccess />}
        </fieldset>

        {!readOnly && (
          <div className="flex justify-end">
            <Button
              htmlType="submit"
              variant={canSubmit ? "primary" : "disabled"}
              dataTestId="role-submit-button"
            >
              {submitLabel}
            </Button>
          </div>
        )}
      </form>
    </FormProvider>
  );
};

export default RoleForm;
