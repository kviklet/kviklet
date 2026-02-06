import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";
import { RoleRequirement } from "../../api/DatasourceApi";

interface RoleProgress {
  roleId: string;
  roleName: string;
  numRequired: number;
  numCurrent: number;
  approverNames: string[];
}

function computeApprovalProgress(
  request: ExecutionRequestResponseWithComments,
): {
  totalRequired: number;
  totalCurrent: number;
  roleProgress: RoleProgress[];
} | null {
  const reviewConfig = request.connection.reviewConfig;
  const roleRequirements = reviewConfig.roleRequirements;

  if (!roleRequirements || roleRequirements.length === 0) {
    return null;
  }

  // Find all approval events (considering only the latest non-reset state)
  const approvals = request.events.filter(
    (e) => e._type === "REVIEW" && e.action === "APPROVE",
  );

  const totalRequired = reviewConfig.numTotalRequired;
  const totalCurrent = approvals.length;

  const roleProgress: RoleProgress[] = roleRequirements.map(
    (req: RoleRequirement) => {
      const approversForRole = approvals.filter(
        (a) =>
          a._type === "REVIEW" &&
          a.author?.roles?.some((role) => role.id === req.roleId),
      );

      const approverNames = approversForRole
        .map((a) => (a._type === "REVIEW" && a.author?.fullName) || "Unknown")
        .filter((name): name is string => name !== null);

      // Get the role name from the first matching approver's role, or fall back to roleId
      const roleName =
        approvals
          .flatMap((a) => (a._type === "REVIEW" ? a.author?.roles ?? [] : []))
          .find((r) => r.id === req.roleId)?.name ?? req.roleId;

      return {
        roleId: req.roleId,
        roleName,
        numRequired: req.numRequired,
        numCurrent: approversForRole.length,
        approverNames,
      };
    },
  );

  return { totalRequired, totalCurrent, roleProgress };
}

export default function ApprovalProgress({
  request,
}: {
  request: ExecutionRequestResponseWithComments;
}) {
  const progress = computeApprovalProgress(request);

  if (!progress) {
    return null;
  }

  const missingRoles = progress.roleProgress.filter(
    (rp) => rp.numCurrent < rp.numRequired,
  );

  return (
    <div className="mb-4 rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
      <h3 className="mb-3 text-sm font-medium text-slate-700 dark:text-slate-300">
        Approval Progress
      </h3>

      <div className="space-y-2">
        {/* Total progress */}
        <ProgressRow
          satisfied={progress.totalCurrent >= progress.totalRequired}
          label={`${progress.totalCurrent} of ${progress.totalRequired} total approval(s)`}
        />

        {/* Role-specific progress */}
        {progress.roleProgress.map((rp) => (
          <ProgressRow
            key={rp.roleId}
            satisfied={rp.numCurrent >= rp.numRequired}
            label={`${rp.numCurrent} of ${rp.numRequired} ${rp.roleName} approval(s)`}
            detail={
              rp.numCurrent > 0
                ? `(${rp.approverNames.join(", ")})`
                : "(waiting)"
            }
          />
        ))}
      </div>

      {/* Status message */}
      {missingRoles.length > 0 && (
        <div className="mt-3 rounded-md border border-yellow-200 bg-yellow-50 p-2.5 dark:border-yellow-800 dark:bg-yellow-900/20">
          <p className="text-xs text-yellow-800 dark:text-yellow-200">
            Waiting for {missingRoles.map((rp) => rp.roleName).join(" and ")}{" "}
            approval before this request can be executed.
          </p>
        </div>
      )}
    </div>
  );
}

function ProgressRow({
  satisfied,
  label,
  detail,
}: {
  satisfied: boolean;
  label: string;
  detail?: string;
}) {
  return (
    <div className="flex items-center gap-3">
      {satisfied ? (
        <div className="flex h-5 w-5 items-center justify-center rounded-full bg-green-100 dark:bg-green-900">
          <svg
            xmlns="http://www.w3.org/2000/svg"
            className="h-3 w-3 text-green-600 dark:text-green-400"
            viewBox="0 0 20 20"
            fill="currentColor"
          >
            <path
              fillRule="evenodd"
              d="M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z"
              clipRule="evenodd"
            />
          </svg>
        </div>
      ) : (
        <div className="flex h-5 w-5 items-center justify-center rounded-full bg-yellow-100 dark:bg-yellow-900">
          <div className="h-2 w-2 rounded-full bg-yellow-500"></div>
        </div>
      )}
      <span className="text-sm text-slate-700 dark:text-slate-300">
        {label}
      </span>
      {detail && (
        <span className="text-xs text-slate-500 dark:text-slate-400">
          {detail}
        </span>
      )}
    </div>
  );
}
