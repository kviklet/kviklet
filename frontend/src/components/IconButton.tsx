import { forwardRef, ReactNode } from "react";

// Quiet toolbar button for panel headers: an icon (or icon plus a short word)
// that only gains a fill on hover so it doesn't compete with the content.
const IconButton = forwardRef<
  HTMLButtonElement,
  {
    label: string;
    onClick: () => void;
    disabled?: boolean;
    children: ReactNode;
    testId?: string;
    className?: string;
  }
>(({ label, onClick, disabled, children, testId, className = "" }, ref) => (
  <button
    ref={ref}
    type="button"
    aria-label={label}
    title={label}
    disabled={disabled}
    onClick={onClick}
    data-testid={testId}
    className={`rounded p-0.5 text-slate-500 transition-colors disabled:cursor-default disabled:text-slate-300 hover:bg-slate-200 hover:text-slate-900 disabled:hover:bg-transparent dark:text-slate-400 dark:disabled:text-slate-700 dark:hover:bg-slate-700 dark:hover:text-slate-50 ${className}`}
  >
    {children}
  </button>
));
IconButton.displayName = "IconButton";

export default IconButton;
