import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";

export default function ApprovalProgress({
  request,
}: {
  request: ExecutionRequestResponseWithComments;
}) {
  const progress = request.approvalProgress;

  // Hide when no progress data or no review requirements
  if (
    !progress ||
    (progress.totalRequired === 0 && progress.roleProgress.length === 0)
  ) {
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
            key={rp.role.id}
            satisfied={rp.numCurrent >= rp.numRequired}
            label={`${rp.numCurrent} of ${rp.numRequired} ${rp.role.name} approval(s)`}
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
            Waiting for {missingRoles.map((rp) => rp.role.name).join(" and ")}{" "}
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
