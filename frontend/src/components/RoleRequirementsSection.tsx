import { useEffect, useState } from "react";
import { LockClosedIcon, PlusIcon, XMarkIcon } from "@heroicons/react/20/solid";
import { RoleResponse, getRoles } from "../api/RoleApi";
import { isApiErrorResponse } from "../api/Errors";
import useConfig from "./ConfigProvider";

interface RoleRequirementField {
  roleId: string;
  numRequired: number;
}

interface RoleRequirementsSectionProps {
  fields: RoleRequirementField[];
  onAppend: (field: RoleRequirementField) => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, field: RoleRequirementField) => void;
  numTotalRequired: number;
}

function useRolesList() {
  const [roles, setRoles] = useState<RoleResponse[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const load = async () => {
      const response = await getRoles();
      if (!isApiErrorResponse(response)) {
        setRoles(response.roles);
      }
      setLoading(false);
    };
    void load();
  }, []);

  return { roles, loading };
}

function getRoleName(roleId: string, roles: RoleResponse[]): string {
  const role = roles.find((r) => r.id === roleId);
  return role?.name ?? roleId;
}

export default function RoleRequirementsSection({
  fields,
  onAppend,
  onRemove,
  onUpdate,
  numTotalRequired,
}: RoleRequirementsSectionProps) {
  const { config } = useConfig();
  const { roles } = useRolesList();
  const licenseValid = config?.licenseValid ?? false;

  return (
    <div className="space-y-3">
      <hr className="border-slate-200 dark:border-slate-700" />

      <div className="flex items-center gap-2">
        <h3 className="text-sm font-medium text-slate-700 dark:text-slate-200">
          Role-Specific Requirements
        </h3>
        <span
          className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium
                     text-purple-800 dark:bg-purple-900 dark:text-purple-200"
        >
          Enterprise
        </span>
      </div>

      <p className="text-xs text-slate-500 dark:text-slate-400">
        Require approvals from specific roles. These are in addition to the
        total reviews required.
      </p>

      {/* State A: License Active - Full Edit */}
      {licenseValid && (
        <LicensedEditUI
          fields={fields}
          roles={roles}
          onAppend={onAppend}
          onRemove={onRemove}
          onUpdate={onUpdate}
        />
      )}

      {/* State B: License Expired + Has Existing Requirements - Read-only with Remove */}
      {!licenseValid && fields.length > 0 && (
        <ExpiredWithRequirementsUI
          fields={fields}
          roles={roles}
          onRemove={onRemove}
        />
      )}

      {/* State C: License Expired + No Existing Requirements - Locked */}
      {!licenseValid && fields.length === 0 && <LockedUI />}

      {/* Info box showing combined requirements */}
      {fields.length > 0 && (
        <div className="rounded-md border border-blue-200 bg-blue-50 p-3 dark:border-blue-800 dark:bg-blue-900/20">
          <p className="text-xs text-blue-800 dark:text-blue-200">
            <strong>Note:</strong> A request needs at least{" "}
            <strong>{numTotalRequired} total approval(s)</strong>
            {fields.map((f, i) => (
              <span key={i}>
                , including{" "}
                <strong>
                  {f.numRequired} from {getRoleName(f.roleId, roles)}
                </strong>
              </span>
            ))}
            .
          </p>
        </div>
      )}
    </div>
  );
}

function LicensedEditUI({
  fields,
  roles,
  onAppend,
  onRemove,
  onUpdate,
}: {
  fields: RoleRequirementField[];
  roles: RoleResponse[];
  onAppend: (field: RoleRequirementField) => void;
  onRemove: (index: number) => void;
  onUpdate: (index: number, field: RoleRequirementField) => void;
}) {
  return (
    <>
      <div className="space-y-2">
        {fields.map((field, index) => (
          <div
            key={index}
            className="flex items-center gap-3 rounded-md bg-slate-100 p-3 dark:bg-slate-700/50"
          >
            <span className="whitespace-nowrap text-sm text-slate-600 dark:text-slate-300">
              Require
            </span>
            <input
              type="number"
              min={1}
              value={field.numRequired}
              onChange={(e) =>
                onUpdate(index, {
                  ...field,
                  numRequired: parseInt(e.target.value) || 1,
                })
              }
              className="w-16 rounded-md border border-slate-300 bg-white px-2 py-1.5
                         text-center text-sm text-slate-900 focus:border-blue-500
                         focus:ring-2 focus:ring-blue-500 dark:border-slate-600
                         dark:bg-slate-700 dark:text-slate-100"
            />
            <span className="whitespace-nowrap text-sm text-slate-600 dark:text-slate-300">
              approval(s) from
            </span>
            <select
              value={field.roleId}
              onChange={(e) =>
                onUpdate(index, { ...field, roleId: e.target.value })
              }
              className="flex-1 rounded-md border border-slate-300 bg-white px-3 py-1.5
                         text-sm text-slate-900 focus:border-blue-500 focus:ring-2
                         focus:ring-blue-500 dark:border-slate-600 dark:bg-slate-700
                         dark:text-slate-100"
            >
              {roles.map((role) => (
                <option key={role.id} value={role.id}>
                  {role.name}
                </option>
              ))}
            </select>
            <button
              type="button"
              onClick={() => onRemove(index)}
              className="rounded p-1.5 text-slate-400 transition-colors
                         hover:bg-red-50 hover:text-red-500 dark:hover:bg-red-900/20
                         dark:hover:text-red-400"
              title="Remove requirement"
            >
              <XMarkIcon className="h-5 w-5" />
            </button>
          </div>
        ))}
      </div>
      <button
        type="button"
        onClick={() => onAppend({ roleId: roles[0]?.id || "", numRequired: 1 })}
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm font-medium
                   text-blue-600 transition-colors hover:bg-blue-50
                   dark:text-blue-400 dark:hover:bg-blue-900/20"
      >
        <PlusIcon className="h-4 w-4" />
        Add Role Requirement
      </button>
    </>
  );
}

function ExpiredWithRequirementsUI({
  fields,
  roles,
  onRemove,
}: {
  fields: RoleRequirementField[];
  roles: RoleResponse[];
  onRemove: (index: number) => void;
}) {
  return (
    <>
      <div className="rounded-md border border-yellow-200 bg-yellow-50 p-3 dark:border-yellow-800 dark:bg-yellow-900/20">
        <p className="text-xs text-yellow-800 dark:text-yellow-200">
          <strong>License expired.</strong> You can remove role requirements but
          cannot add or modify them.
        </p>
      </div>
      <div className="space-y-2">
        {fields.map((field, index) => (
          <div
            key={index}
            className="flex items-center gap-3 rounded-md bg-slate-100 p-3 opacity-75 dark:bg-slate-700/30"
          >
            <span className="text-sm text-slate-500">Require</span>
            <span className="w-16 px-2 py-1.5 text-center text-sm text-slate-600 dark:text-slate-400">
              {field.numRequired}
            </span>
            <span className="text-sm text-slate-500">approval(s) from</span>
            <span className="flex-1 px-3 py-1.5 text-sm text-slate-600 dark:text-slate-400">
              {getRoleName(field.roleId, roles)}
            </span>
            <button
              type="button"
              onClick={() => onRemove(index)}
              className="rounded p-1.5 text-red-500 transition-colors hover:bg-red-50
                         dark:hover:bg-red-900/20"
              title="Remove requirement"
            >
              <XMarkIcon className="h-5 w-5" />
            </button>
          </div>
        ))}
      </div>
    </>
  );
}

function LockedUI() {
  return (
    <div className="relative opacity-60">
      <div className="absolute inset-0 z-10 flex items-center justify-center">
        <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 py-2 shadow-lg dark:border-slate-600 dark:bg-slate-800">
          <LockClosedIcon className="h-5 w-5 text-slate-500" />
          <span className="text-sm font-medium text-slate-700 dark:text-slate-300">
            Enterprise feature
          </span>
        </div>
      </div>
      <div className="pointer-events-none">
        <div className="h-20 rounded-md bg-slate-100 dark:bg-slate-700/50"></div>
      </div>
    </div>
  );
}

export type { RoleRequirementField };
