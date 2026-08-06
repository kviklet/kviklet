import { useNavigate } from "react-router-dom";
import Button from "./Button";

type Props = {
  /** What the user tried to reach, e.g. "the role settings". */
  resource?: string;
  message?: string;
  showBackButton?: boolean;
};

/**
 * Friendly forbidden state for surfaces reached without the required permission — a direct URL, a
 * stale link, or a race with a role change. Prefer hiding the entry point in the first place; this
 * is the backstop, so it should say plainly that access is missing rather than pretend the page is
 * empty.
 */
export default function NotAuthorized({
  resource,
  message,
  showBackButton = true,
}: Props) {
  const navigate = useNavigate();
  // React Router tracks its position in history.state.idx; at 0 there is no in-app
  // page to go back to (direct URL, stale link), so "back" would be a no-op or leave
  // the app — send those users to the index instead.
  const canGoBack =
    ((window.history.state as { idx?: number } | null)?.idx ?? 0) > 0;

  return (
    <div
      className="flex h-64 flex-col items-center justify-center gap-4 rounded-lg border border-slate-200 bg-white dark:border-slate-700 dark:bg-slate-900"
      data-testid="not-authorized"
    >
      <div className="flex flex-col items-center gap-1 px-4 text-center">
        <h2 className="text-lg text-slate-900 dark:text-slate-100">
          You don&apos;t have access to {resource ?? "this page"}
        </h2>
        <p className="text-slate-500 dark:text-slate-400">
          {message ?? "Ask an administrator to grant your role access."}
        </p>
      </div>
      {showBackButton && (
        <Button
          onClick={() =>
            canGoBack ? navigate(-1) : navigate("/", { replace: true })
          }
        >
          {canGoBack ? "Go back" : "Go to home"}
        </Button>
      )}
    </div>
  );
}
