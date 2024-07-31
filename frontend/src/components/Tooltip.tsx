import React, { useState } from "react";

interface TooltipProps {
  content: string;
  children: React.ReactNode;
  position?: "top" | "bottom" | "left" | "right";
}

const Tooltip: React.FC<TooltipProps> = ({
  content,
  children,
  position = "top",
}) => {
  const [isVisible, setIsVisible] = useState(false);

  const positionClasses = {
    top: "bottom-full left-1/2 transform -translate-x-1/2 mb-2",
    bottom: "top-full left-1/2 transform -translate-x-1/2 mt-2",
    left: "right-full top-1/2 transform -translate-y-1/2 mr-2",
    right: "left-full top-1/2 transform -translate-y-1/2 ml-2",
  };

  return (
    <div className="relative inline-block">
      <div
        onMouseEnter={() => setIsVisible(true)}
        onMouseLeave={() => setIsVisible(false)}
      >
        {children}
      </div>
      <div
        className={`max-w-48 absolute z-10 rounded-md bg-slate-800 px-3 py-2 text-xs font-medium text-white shadow-lg transition-opacity duration-300 ease-in-out dark:bg-slate-700 ${
          isVisible ? "visible opacity-100" : "invisible opacity-0"
        } ${positionClasses[position]}`}
        role="tooltip"
      >
        <div className="inline-block whitespace-nowrap text-center">
          {content}
        </div>
        <div
          className={`absolute h-2 w-2 rotate-45 transform bg-slate-800 dark:bg-slate-700 ${
            position === "top"
              ? "bottom-[-4px] left-1/2 -translate-x-1/2"
              : position === "bottom"
              ? "left-1/2 top-[-4px] -translate-x-1/2"
              : position === "left"
              ? "right-[-4px] top-1/2 -translate-y-1/2"
              : "left-[-4px] top-1/2 -translate-y-1/2"
          }`}
        />
      </div>
    </div>
  );
};

export default Tooltip;
