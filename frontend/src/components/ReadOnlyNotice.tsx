import { ReactNode } from "react";

type Props = {
  /** What is shown read-only, e.g. "this role" or "these settings". */
  resource?: string;
  className?: string;
  /** Custom copy; replaces the default sentence built from `resource`. */
  children?: ReactNode;
};

/**
 * Gray one-liner for pages rendered read-only because the user lacks the edit
 * permission. Pair with actually removing the edit affordances — this explains
 * the absence, it doesn't enforce it.
 */
export default function ReadOnlyNotice({
  resource = "this page",
  className,
  children,
}: Props) {
  return (
    <p
      className={`text-sm text-slate-500 dark:text-slate-400${
        className ? ` ${className}` : ""
      }`}
    >
      {children ??
        `You can view ${resource} but lack the permission to change ${
          resource.startsWith("these") ? "them" : "it"
        }.`}
    </p>
  );
}
