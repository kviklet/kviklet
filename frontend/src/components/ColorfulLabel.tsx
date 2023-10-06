import { solid } from "@fortawesome/fontawesome-svg-core/import.macro";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import React from "react";

const capitalizeFirstLetter = ([first, ...rest]: string) =>
  first.toUpperCase() + rest.join("");

const colorFromText = (text: string) => {
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
    "bg-blue-600",
    "bg-green-600",
    "bg-yellow-600",
    "bg-red-600",
    "bg-indigo-600",
    "bg-purple-600",
    "bg-pink-600",
    "bg-cyan-600",
    "bg-lime-600",
    "bg-emerald-600",
  ];

  return colors[Math.abs(djb2(text)) % colors.length];
};

const ColorfulLabel = (props: {
  text: string;
  onDelete?: (event: React.MouseEvent<HTMLButtonElement, MouseEvent>) => void;
  onClick?: (event: React.MouseEvent<HTMLDivElement, MouseEvent>) => void;
  color?: string;
}) => {
  const color = props.color || colorFromText(props.text);

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
export { colorFromText };
