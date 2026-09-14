/**
 * A user's display name, and the single place that decides how a deactivated account looks.
 * Deactivated names are muted wherever they appear (request lists, review pages, filters,
 * settings) so historical records stay readable while making clear the person no longer has
 * access. Pass `badge` where there is room for an explicit "Deactivated" chip; elsewhere the
 * reason is carried by the tooltip and by text for screen readers.
 */
const UserName = (props: {
  user: { fullName?: string | null; email?: string; active?: boolean };
  className?: string;
  badge?: boolean;
}) => {
  const name = props.user.fullName || props.user.email;
  if (props.user.active !== false) {
    return <span className={props.className}>{name}</span>;
  }
  if (!props.badge) {
    // The muted color sits on an inner span so it wins over any color in `className`.
    return (
      <span className={props.className}>
        <span
          className="text-slate-400 dark:text-slate-500"
          title="This account has been deactivated"
        >
          {name}
        </span>
        <span className="sr-only"> (deactivated)</span>
      </span>
    );
  }
  // max-w-full keeps the pair inside its container so the name truncates before the
  // badge gets clipped.
  return (
    <span
      className={`inline-flex max-w-full items-center gap-2 ${
        props.className ?? ""
      }`}
    >
      <span
        className="min-w-0 truncate text-slate-400 dark:text-slate-500"
        title="This account has been deactivated"
      >
        {name}
      </span>
      <DeactivatedBadge />
    </span>
  );
};

export const DeactivatedBadge = () => (
  <span
    data-testid="deactivated-badge"
    className="inline-flex shrink-0 items-center rounded border border-slate-300 bg-slate-100 px-1.5 py-0.5 text-xs font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-400"
  >
    Deactivated
  </span>
);

export default UserName;
