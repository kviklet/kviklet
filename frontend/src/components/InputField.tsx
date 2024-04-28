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
        <div className="flex justify-between w-full">
          <label
            htmlFor={props.id}
            className="my-auto text-sm font-medium text-slate-700 dark:text-slate-200 mr-auto"
          >
            {label}
          </label>
          <input
            type={inputType || "text"}
            className={`basis-3/5 appearance-none block w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 text-sm transition-colors ${props.className}`}
            autoComplete={
              props.type === "passwordlike" ? "new-password" : undefined
            }
            {...props}
            ref={ref}
          />
        </div>
        <p className="text-sm text-red-500 dark:text-red-400 float-right">
          {props.error && props.error}
        </p>
      </div>
    );
  },
);

export default InputField;
