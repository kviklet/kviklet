function Button(props: {
  id?: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  children: React.ReactNode;
  className?: string;
  type?:
    | "button"
    | "submit"
    | "reset"
    | "disabled"
    | "primary"
    | "danger"
    | undefined;
}) {
  const submitStyle =
    "bg-sky-700 font-semibold text-white hover:bg-sky-900 dark:hover:bg-sky-500 dark:bg-sky-500 dark:text-white";
  const disabledStyle =
    "bg-gray-300 text-gray-500 hover:bg-gray-300 hover:border-gray-300";
  const defaultStyle =
    "border border-gray-300 hover:border-gray-400 dark:border-slate-700 dark:hover:border-slate-500 dark:bg-slate-800 dark:text-slate-50 transition-colors";
  const dangerStyle = "bg-red-600 text-white hover:bg-red-800"; // this is your new "danger" style

  const disabled = props.type == "disabled" ? true : undefined;
  const submit = props.type == "submit" ? "submit" : undefined;

  return (
    <button
      id={props.id}
      onClick={props.onClick}
      type={submit}
      disabled={disabled}
      className={`${props.className} px-4 py-2  text-md ${
        (props.type == "submit" && submitStyle) ||
        (props.type == "disabled" && disabledStyle) ||
        (props.type == "danger" && dangerStyle) ||
        defaultStyle
      }
      leading-5 align-middle  rounded-md`}
    >
      {props.children}
    </button>
  );
}

export default Button;
