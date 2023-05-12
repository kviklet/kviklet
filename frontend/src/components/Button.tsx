function Button(props: {
  id: string;
  onClick: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  children: React.ReactNode;
  className?: string;
  type?: "button" | "submit" | "reset" | undefined;
}) {
  return (
    <button
      id={props.id}
      className={`${props.className} px-4 py-2  text-md ${
        (props.type == "submit" &&
          "bg-green-700 font-semibold text-white hover:bg-green-900") ||
        "border border-gray-300 hover:border-gray-400"
      } leading-5 align-middle  rounded-lg`}
    >
      {props.children}
    </button>
  );
}

export default Button;
