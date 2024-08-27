function Button(props: {
  id?: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  children: React.ReactNode;
  className?: string;
  size?: "sm" | "md" | "lg";
  textSize?: "sm" | "md" | "lg";
  type?:
    | "button"
    | "submit"
    | "reset"
    | "disabled"
    | "primary"
    | "danger"
    | undefined;
  dataTestId?: string;
}) {
  const submitStyle =
    "bg-indigo-700 font-semibold text-white hover:bg-sky-900 dark:hover:bg-indigo-600 dark:bg-indigo-700 dark:text-slate-50 transition-colors dark:shadow-sm";
  const disabledStyle =
    "bg-slate-300 text-slate-500 hover:bg-slate-300 hover:border-slate-300 dark:bg-slate-700 dark:text-slate-500 dark:hover:bg-slate-700 dark:hover:border-slate-700 dark:hover:text-slate-500";
  const defaultStyle =
    "border border-gray-300 hover:border-gray-400 dark:border-slate-700 dark:hover:border-slate-500 dark:bg-slate-800 dark:text-slate-50 transition-colors";
  const dangerStyle =
    "bg-red-600 text-white hover:bg-red-800 transition-colors dark:hover:bg-red-400"; // this is your new "danger" style

  const disabled = props.type == "disabled" ? true : undefined;
  const submit = props.type == "submit" ? "submit" : undefined;

  const size = props.size == "sm" ? "px-2 py-1" : "px-4 py-2";
  const textSize =
    (props.textSize || props.size) == "sm" ? "text-sm" : "text-base";

  return (
    <button
      id={props.id}
      onClick={props.onClick}
      type={submit}
      disabled={disabled}
      data-testid={props.dataTestId}
      className={`${props.className} ${size} ${textSize} ${
        (props.type == "submit" && submitStyle) ||
        (props.type == "disabled" && disabledStyle) ||
        (props.type == "danger" && dangerStyle) ||
        defaultStyle
      }
      rounded-md align-middle  leading-5`}
    >
      {props.children}
    </button>
  );
}

export default Button;
