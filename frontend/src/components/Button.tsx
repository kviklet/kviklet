function Button(props: {
  id?: string;
  onClick?: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  children: React.ReactNode;
  className?: string;
  type?: "button" | "submit" | "reset" | "disabled" | "primary" | undefined;
}) {
  const submitStyle = "bg-sky-700 font-semibold text-white hover:bg-sky-900";
  const disabledStyle =
    "bg-gray-300 text-gray-500 hover:bg-gray-300 hover:border-gray-300";
  const defaultStyle = "border border-gray-300 hover:border-gray-400";

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
        defaultStyle
      }
      leading-5 align-middle  rounded-lg`}
    >
      {props.children}
    </button>
  );
}

export default Button;
