import { QuestionMarkCircleIcon } from "@heroicons/react/20/solid";
import { forwardRef } from "react";

type InputFieldProps = {
  stacked?: boolean | undefined;
  tooltip?: string;
  label: string;
  type?: string;
  id?: string;
  className?: string;
  error?: string;
  [x: string]: unknown;
};

const InputField = forwardRef<HTMLInputElement, InputFieldProps>(
  ({ label, ...props }: InputFieldProps, ref) => {
    const inputType = props.type === "passwordlike" ? "password" : props.type;
    const type = props.type;
    delete props.type;

    const isCheckbox = inputType === "checkbox";

    const inputClassName = isCheckbox
      ? "h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-500"
      : `block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3 
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500 ${props.className}`;

    const labelClassName = isCheckbox
      ? "ml-2 text-sm font-medium text-slate-700 dark:text-slate-200"
      : "my-auto mr-auto flex items-center text-sm font-medium text-slate-700 dark:text-slate-200";

    const renderInput = () => (
      <input
        type={inputType || "text"}
        className={inputClassName}
        autoComplete={type === "passwordlike" ? "new-password" : undefined}
        {...props}
        ref={ref}
      />
    );

    const renderLabel = () => (
      <label
        htmlFor={props.id}
        className={labelClassName}
        title={props.tooltip || undefined}
      >
        {label}
        {props.tooltip && (
          <QuestionMarkCircleIcon className="ml-1 inline-block h-4 w-4 text-slate-400" />
        )}
      </label>
    );

    if (props.stacked) {
      return (
        <div className="flex flex-col">
          {!isCheckbox && renderLabel()}
          <div className={`mt-2 ${isCheckbox ? "flex items-center" : ""}`}>
            {renderInput()}
            {isCheckbox && renderLabel()}
          </div>
          {props.error && (
            <span className="mt-1 text-sm text-red-500">{props.error}</span>
          )}
        </div>
      );
    } else {
      return (
        <div className="flex-col">
          <div
            className={`flex w-full ${
              isCheckbox ? "items-center" : "justify-between"
            }`}
          >
            {!isCheckbox && renderLabel()}
            {isCheckbox ? (
              <>
                {renderInput()}
                {renderLabel()}
              </>
            ) : (
              renderInput()
            )}
          </div>
          {props.error && (
            <p className="mt-1 text-sm text-red-500 dark:text-red-400">
              {props.error}
            </p>
          )}
        </div>
      );
    }
  },
);

// Input field like above but with Text area
const TextField = forwardRef<HTMLTextAreaElement, InputFieldProps>(
  ({ label, ...props }: InputFieldProps, ref) => {
    return (
      <div className="flex-col">
        <div className="flex w-full justify-between">
          <label
            htmlFor={props.id}
            className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
          >
            {label}
          </label>
          <textarea
            className={`block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3 
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500 ${props.className}`}
            {...props}
            ref={ref}
          />
        </div>
        <p className="float-right text-sm text-red-500 dark:text-red-400">
          {props.error && props.error}
        </p>
      </div>
    );
  },
);

export default InputField;

export { TextField };
