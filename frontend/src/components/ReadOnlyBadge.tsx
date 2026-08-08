import { LockClosedIcon } from "@heroicons/react/20/solid";
import Tooltip from "./Tooltip";

type Props = {
  /** Why the surface is read-only, shown on hover. Keep it to one short sentence. */
  tooltip: string;
};

/**
 * "Read-only" chip for the heading row of a page that still renders its edit form in a
 * disabled state. Prominence comes from sitting next to the title, not from alarm colors —
 * read-only is a normal state, not a warning. Pages with a dedicated read-only view don't
 * need this; the view speaks for itself.
 */
export default function ReadOnlyBadge({ tooltip }: Props) {
  return (
    <Tooltip content={tooltip} position="bottom">
      <span
        data-testid="read-only-badge"
        className="inline-flex items-center gap-1 rounded border border-slate-300 bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300"
      >
        <LockClosedIcon className="h-3.5 w-3.5" />
        Read-only
      </span>
    </Tooltip>
  );
}
