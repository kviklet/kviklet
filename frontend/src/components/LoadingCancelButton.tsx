import React, { useState } from "react";
import { XMarkIcon } from "@heroicons/react/20/solid";

function LoadingCancelButton(props: {
  id?: string;
  onClick: () => Promise<void>;
  onCancel: () => void;
  children: React.ReactNode;
  className?: string;
  size?: "sm" | "md" | "lg";
  textSize?: "sm" | "md" | "lg";
  type?: "button" | "submit" | "reset" | "primary" | "danger" | undefined;
  disabled?: boolean;
  dataTestId?: string;
}) {
  const [isLoading, setIsLoading] = useState(false);

  const submitStyle =
    "bg-indigo-700 font-semibold text-white hover:bg-sky-900 dark:hover:bg-indigo-600 dark:bg-indigo-700 dark:text-slate-50 transition-colors dark:shadow-sm";
  const dangerStyle =
    "bg-red-600 text-white hover:bg-red-800 transition-colors dark:hover:bg-red-400";
  const defaultStyle =
    "border border-gray-300 hover:border-gray-400 dark:border-slate-700 dark:hover:border-slate-500 dark:bg-slate-800 dark:text-slate-50 transition-colors";
  const disabledStyle =
    "bg-slate-300 text-slate-500 hover:bg-slate-300 hover:border-slate-300 dark:bg-slate-700 dark:text-slate-500 dark:hover:bg-slate-700 dark:hover:border-slate-700 dark:hover:text-slate-500 cursor-not-allowed";

  const size = props.size === "sm" ? "px-2 py-1" : "px-4 py-2";
  const textSize =
    (props.textSize || props.size) === "sm" ? "text-sm" : "text-base";

  const handleClick = async () => {
    if (props.disabled) return;
    setIsLoading(true);
    try {
      await props.onClick();
    } finally {
      setIsLoading(false);
    }
  };

  const handleCancel = () => {
    if (props.disabled) return;
    props.onCancel();
    setIsLoading(false);
  };

  const getButtonStyle = () => {
    if (props.disabled) return disabledStyle;
    if (props.type === "submit") return submitStyle;
    if (props.type === "danger") return dangerStyle;
    return defaultStyle;
  };

  return (
    <button
      id={props.id}
      onClick={isLoading ? handleCancel : handleClick}
      type={props.type === "submit" ? "submit" : "button"}
      disabled={props.disabled}
      className={`${
        props.className
      } ${size} ${textSize} ${getButtonStyle()} flex items-center justify-center rounded-md align-middle leading-5`}
      data-testid={props.dataTestId}
    >
      {isLoading ? (
        <>
          <span className="mr-2">Cancel</span>
          <XMarkIcon className="h-5 w-5" aria-hidden="true" />
        </>
      ) : (
        props.children
      )}
    </button>
  );
}

export default LoadingCancelButton;
