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
    "dark:bg-blue-600 dark:text-white bg-slate-50 border-blue-600 text-blue-800",
    "dark:bg-green-600 dark:text-white bg-slate-50 border-green-600 text-green-800",
    "dark:bg-yellow-600 dark:text-white bg-slate-50 border-yellow-600 text-yellow-800",
    "dark:bg-red-600 dark:text-white bg-slate-50 border-red-600 text-red-800",
    "dark:bg-indigo-600 dark:text-white bg-slate-50 border-indigo-600 text-indigo-800",
    "dark:bg-purple-600 dark:text-white bg-slate-50 border-purple-600 text-purple-800",
    "dark:bg-pink-600 dark:text-white bg-slate-50 border-ping-600 text-pink-800",
    "dark:bg-cyan-600 dark:text-white bg-slate-50 border-cyan-600 text-cyan-800",
    "dark:bg-lime-600 dark:text-white bg-slate-50 border-lime-600 text-lime-800",
    "dark:bg-emerald-600 dark:text-white bg-slate-50 border-emerald-600 text-emerald-800",
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
      }  m-1 rounded-full border px-2 py-1 text-sm dark:border-none`}
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
