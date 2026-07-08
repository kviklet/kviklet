import React from "react";

const sizeClasses = {
  sm: "h-5 w-5 border-2",
  md: "h-8 w-8 border-[3px]",
  lg: "h-12 w-12 border-4",
};

const Spinner: React.FC<{ size?: "sm" | "md" | "lg"; page?: boolean }> = ({
  size = "md",
  page = false,
}) => {
  return (
    <div
      className={`flex items-center justify-center ${
        page ? "min-h-[50vh] w-full" : ""
      }`}
    >
      <div
        className={`${sizeClasses[size]} animate-spin rounded-full border-indigo-500 border-r-transparent`}
      ></div>
    </div>
  );
};

export default Spinner;
