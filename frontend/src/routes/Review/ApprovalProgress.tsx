import { CheckIcon } from "@heroicons/react/20/solid";
import { ExecutionRequestResponseWithComments } from "../../api/ExecutionRequestApi";

export default function ApprovalProgress({
  request,
}: {
  request: ExecutionRequestResponseWithComments;
}) {
  const progress = request.approvalProgress;

  if (
    !progress ||
    (progress.totalRequired === 0 && progress.roleProgress.length === 0)
  ) {
    return null;
  }

  const totalSatisfied = progress.totalCurrent >= progress.totalRequired;

  return (
    <div className="flex flex-col gap-1 text-xs text-slate-600 dark:text-slate-400">
      {progress.roleProgress.map((rp) => {
        const satisfied = rp.numCurrent >= rp.numRequired;
        return (
          <ProgressItem key={rp.role.id} satisfied={satisfied}>
            {rp.role.name} {rp.numCurrent}/{rp.numRequired}
          </ProgressItem>
        );
      })}
      {progress.totalRequired > 0 && (
        <ProgressItem satisfied={totalSatisfied}>
          Total {progress.totalCurrent}/{progress.totalRequired}
        </ProgressItem>
      )}
    </div>
  );
}

function ProgressItem({
  satisfied,
  children,
}: {
  satisfied: boolean;
  children: React.ReactNode;
}) {
  return (
    <span className="flex items-center gap-1">
      {satisfied ? (
        <CheckIcon className="h-3.5 w-3.5 text-green-500 dark:text-green-400" />
      ) : (
        <span className="flex h-3.5 w-3.5 items-center justify-center">
          <span className="h-1.5 w-1.5 rounded-full bg-yellow-500"></span>
        </span>
      )}
      {children}
    </span>
  );
}
