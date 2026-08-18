import { MagnifyingGlassIcon } from "@heroicons/react/20/solid";
import { ChangeEvent, forwardRef } from "react";

type SearchInputProps = {
  value: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  className?: string;
  // Matches the h-7 filter-pill height so search can share a row with pills
  compact?: boolean;
};

const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(
  (
    {
      value,
      onChange,
      placeholder = "Search connections...",
      className = "",
      compact = false,
    },
    ref,
  ) => {
    return (
      <div className={`${className} relative w-full`}>
        <MagnifyingGlassIcon
          className={`absolute top-1/2 -translate-y-1/2 text-slate-400 ${
            compact ? "left-2.5 h-4 w-4" : "left-3 h-5 w-5"
          }`}
        />
        <input
          type="text"
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          ref={ref}
          className={`w-full rounded-md border-slate-300 placeholder:text-slate-400
            focus:outline-none focus:ring-1 focus:ring-indigo-600
            dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100
            dark:placeholder:text-slate-500 dark:focus:border-indigo-500
            dark:focus:ring-indigo-500 ${
              compact ? "h-7 py-1 pl-8 pr-3 text-xs" : "py-2 pl-10 pr-4 text-sm"
            }`}
        />
      </div>
    );
  },
);

export default SearchInput;
