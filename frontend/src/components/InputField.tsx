function InputField(props: {
  id: string;
  name: string;
  type?: string;
  placeholder?: string;
  value?: string;
  onChange?: (event: React.ChangeEvent<HTMLInputElement>) => void;
}) {
  return (
    <div className="flex m-2">
      <label htmlFor={props.id} className="my-auto ml-5 pl-1.5 mr-auto">
        {props.name}
      </label>
      <input
        type={props.type || "text"}
        className="basis-2/3 focus:border-blue-600 my-auto appearance-none border rounded w-full mx-1 py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
        placeholder={props.placeholder || ""}
        name={props.id}
        id={props.id}
        value={props.value}
        onChange={props.onChange}
      />
    </div>
  );
}

export default InputField;
