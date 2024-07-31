import { MagnifyingGlassIcon } from "@heroicons/react/20/solid";
import { forwardRef } from "react";

type SearchInputProps = {
  value: string;
  onChange: (event: React.ChangeEvent<HTMLInputElement>) => void;
  placeholder?: string;
  className?: string;
};

const SearchInput = forwardRef<HTMLInputElement, SearchInputProps>(
  (
    { value, onChange, placeholder = "Search connections...", className = "" },
    ref,
  ) => {
    return (
      <div className="relative">
        <MagnifyingGlassIcon className="absolute left-3 top-1/2 h-5 w-5 -translate-y-5 text-slate-400" />
        <input
          type="text"
          value={value}
          onChange={onChange}
          placeholder={placeholder}
          ref={ref}
          className={`w-full rounded-md border-slate-300 py-2 pl-10 pr-4 text-sm 
            placeholder:text-slate-400 focus:border-indigo-600 focus:ring-indigo-600
            dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 
            dark:placeholder:text-slate-500 dark:focus:border-indigo-500 
            dark:focus:ring-indigo-500 ${className}`}
        />
      </div>
    );
  },
);

export default SearchInput;
