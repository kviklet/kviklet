import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

const capitalizeFirstLetter = ([first, ...rest]: string) =>
  first.toUpperCase() + rest.join("");

const ColorfulLabel = (props: {
  text: string;
  onDelete?: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  onClick?: (event: React.MouseEvent<HTMLDivElement, MouseEvent>) => void;
  color?: string;
}) => {
  // hash the label text to get a color inside the tailwindcss color palette
  // https://tailwindcss.com/docs/customizing-colors#color-palette-reference
  const djb2 = (str: string) => {
    let hash = 5381;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = (hash << 5) + hash + char; /* hash * 33 + char */
    }
    return hash;
  };
  const colors = [
    "bg-blue-500",
    "bg-green-500",
    "bg-yellow-500",
    "bg-red-500",
    "bg-indigo-500",
    "bg-purple-500",
    "bg-pink-500",
    "bg-cyan-500",
    "bg-lime-500",
    "bg-emerald-500",
  ];

  const color =
    props.color || colors[Math.abs(djb2(props.text)) % colors.length];

  return (
    <div
      onClick={props.onClick}
      className={`${color} ${
        props.onClick && "cursor-pointer"
      } text-white text-sm rounded-full px-2 py-1 m-1`}
    >
      {capitalizeFirstLetter(props.text)}

      {props.onDelete && (
        <button onClick={props.onDelete} className="ml-2">
          <FontAwesomeIcon icon={solid("times")} />
        </button>
      )}
    </div>
  );
};

export default ColorfulLabel;
