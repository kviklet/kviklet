import { forwardRef } from "react";

type InputFieldProps = {
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

    return (
      <div className="flex-col">
        <div className="flex w-full justify-between">
          <label
            htmlFor={props.id}
            className="my-auto mr-auto text-sm font-medium text-slate-700 dark:text-slate-200"
          >
            {label}
          </label>
          <input
            type={inputType || "text"}
            className={`block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3 
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500 ${props.className}`}
            autoComplete={
              props.type === "passwordlike" ? "new-password" : undefined
            }
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
