import { ReactNode } from "react";

const inputBaseClassName =
  "block appearance-none rounded-md border border-slate-300 px-3 py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none hover:border-slate-400 focus:hover:border-indigo-600 disabled:cursor-not-allowed disabled:opacity-60 dark:border-slate-700 dark:bg-slate-900 dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500";

const inputClassName = `w-full ${inputBaseClassName}`;

const checkboxClassName =
  "mt-0.5 h-4 w-4 shrink-0 rounded border-slate-300 text-indigo-600 focus:ring-indigo-600 disabled:cursor-not-allowed disabled:opacity-60";

/** A titled card that groups the related rows of a settings form. */
function FormSection(props: {
  title: string;
  description?: ReactNode;
  badge?: ReactNode;
  children: ReactNode;
  testId?: string;
}) {
  return (
    <section
      className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-700 dark:bg-slate-900"
      data-testid={props.testId}
    >
      <div className="mb-4">
        <div className="flex items-center gap-2">
          <h2 className="text-base font-semibold text-slate-900 dark:text-slate-50">
            {props.title}
          </h2>
          {props.badge}
        </div>
        {props.description && (
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
            {props.description}
          </p>
        )}
      </div>
      <div className="flex flex-col gap-3">{props.children}</div>
    </section>
  );
}

/**
 * One labelled row: label on the left, control on the right, stacked on narrow screens.
 * The hint sits under the control and replaces hover-only tooltips.
 */
function FormRow(props: {
  label: string;
  htmlFor?: string;
  hint?: ReactNode;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1 sm:flex-row sm:items-start sm:gap-4">
      <label
        htmlFor={props.htmlFor}
        className="text-sm font-medium text-slate-700 dark:text-slate-200 sm:w-2/5 sm:pt-2"
      >
        {props.label}
      </label>
      <div className="min-w-0 sm:w-3/5">
        {props.children}
        {props.hint && (
          <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
            {props.hint}
          </p>
        )}
        {props.error && (
          <p className="mt-1 text-sm text-red-600 dark:text-red-400">
            {props.error}
          </p>
        )}
      </div>
    </div>
  );
}

/** A checkbox with its label and a one-line explanation next to it. */
function CheckboxRow(props: {
  id: string;
  label: string;
  description?: ReactNode;
  /** The checkbox itself, so callers can spread react-hook-form's register() onto it. */
  children: ReactNode;
}) {
  return (
    <div className="flex items-start gap-3">
      {props.children}
      <label htmlFor={props.id} className="text-sm">
        <span className="block font-medium text-slate-700 dark:text-slate-200">
          {props.label}
        </span>
        {props.description && (
          <span className="block text-slate-500 dark:text-slate-400">
            {props.description}
          </span>
        )}
      </label>
    </div>
  );
}

export {
  FormSection,
  FormRow,
  CheckboxRow,
  inputClassName,
  inputBaseClassName,
  checkboxClassName,
};
