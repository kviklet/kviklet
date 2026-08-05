const Toggle = (props: {
  active: boolean;
  onClick: () => void;
  disabled?: boolean;
  tooltip?: string;
}) => {
  return (
    <label
      className={`relative z-0 inline-flex items-center ${
        props.disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer"
      }`}
      onClick={props.disabled ? undefined : props.onClick}
      title={props.tooltip}
    >
      <input
        type="checkbox"
        checked={props.active}
        disabled={props.disabled}
        className="peer sr-only"
        readOnly
        onClick={(event) => event.stopPropagation()}
      />
      <div
        className="peer h-6 w-11 rounded-full bg-slate-200
      after:absolute after:left-[2px] after:top-0.5 after:h-5 after:w-5 after:rounded-full after:border after:border-gray-300 after:bg-white after:transition-all after:content-['']
      peer-checked:bg-indigo-800 peer-checked:after:translate-x-full peer-checked:after:border-white
      dark:border-gray-600 dark:bg-slate-700"
      ></div>
    </label>
  );
};

export default Toggle;
