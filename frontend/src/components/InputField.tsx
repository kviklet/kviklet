function InputField(props: {
  id: string;
  name: string;
  type?: string;
  placeholder?: string;
  value?: string | number;
  className?: string;
  onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  const inputType = props.type === "passwordlike" ? "password" : props.type;

  return (
    <div className="flex m-2">
      <label
        htmlFor={props.id}
        className="my-auto text-sm font-medium text-slate-700 dark:text-slate-200 ml-2 pl-1.5 mr-auto"
      >
        {props.name}
      </label>
      <input
        type={inputType || "text"}
        className={`basis-3/5 appearance-none block w-full px-3 py-2 rounded-md border 
        border-slate-300 dark:bg-slate-900 hover:border-slate-400 focus:border-indigo-600 focus:hover:border-indigo-600
        focus:outline-none dark:hover:border-slate-600 dark:hover:focus:border-gray-500 dark:border-slate-700
         dark:focus:border-gray-500 text-sm transition-colors ${props.className}`}
        placeholder={props.placeholder || ""}
        name={props.id}
        id={props.id}
        value={props.value}
        onChange={props.onChange}
        autoComplete={
          props.type === "passwordlike" ? "new-password" : undefined
        }
      />
    </div>
  );
}

export default InputField;
