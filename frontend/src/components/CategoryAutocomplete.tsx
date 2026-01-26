import {
  Combobox,
  ComboboxButton,
  ComboboxInput,
  ComboboxOption,
  ComboboxOptions,
} from "@headlessui/react";
import { ChevronUpDownIcon, XMarkIcon } from "@heroicons/react/20/solid";
import { ChangeEvent, useState } from "react";

interface CategoryAutocompleteProps {
  value: string | null | undefined;
  onChange: (value: string | null) => void;
  availableCategories: string[];
  placeholder?: string;
}

function classNames(...classes: (string | boolean)[]) {
  return classes.filter(Boolean).join(" ");
}

export default function CategoryAutocomplete({
  value,
  onChange,
  availableCategories,
  placeholder = "Select or type a category",
}: CategoryAutocompleteProps) {
  const [query, setQuery] = useState("");

  const filteredCategories =
    query === ""
      ? availableCategories
      : availableCategories.filter((category) =>
          category.toLowerCase().includes(query.toLowerCase()),
        );

  const handleSelect = (selected: string | null) => {
    onChange(selected);
    setQuery("");
  };

  const handleClear = () => {
    onChange(null);
    setQuery("");
  };

  // Check if query exactly matches an existing category (case-insensitive)
  const queryExactlyMatchesExisting = availableCategories.some(
    (cat) => cat.toLowerCase() === query.toLowerCase(),
  );

  return (
    <div className="relative basis-3/5">
      <Combobox value={value ?? null} onChange={handleSelect}>
        <div className="relative">
          <ComboboxInput
            className="block w-full appearance-none rounded-md border border-slate-300 px-3
              py-2 pr-16 text-sm transition-colors focus:border-indigo-600 focus:outline-none
              hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
              dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500"
            displayValue={(cat: string | null) => cat ?? ""}
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              setQuery(event.target.value)
            }
            placeholder={placeholder}
          />
          <div className="absolute inset-y-0 right-0 flex items-center">
            {value && (
              <button
                type="button"
                onClick={handleClear}
                className="px-1 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300"
              >
                <XMarkIcon className="h-5 w-5" aria-hidden="true" />
              </button>
            )}
            <ComboboxButton className="flex items-center rounded-r-md px-2 focus:outline-none">
              <ChevronUpDownIcon
                className="h-5 w-5 text-slate-400"
                aria-hidden="true"
              />
            </ComboboxButton>
          </div>
        </div>

        <ComboboxOptions className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-slate-50 py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 sm:text-sm">
          {filteredCategories.map((category) => (
            <ComboboxOption
              key={category}
              value={category}
              className={({ focus }) =>
                classNames(
                  "relative cursor-default select-none py-2 pl-3 pr-9",
                  focus
                    ? "bg-indigo-600 text-slate-50"
                    : "text-slate-900 dark:text-slate-50",
                )
              }
            >
              {category}
            </ComboboxOption>
          ))}
          {/* Show "Create category" option when query doesn't exactly match existing */}
          {query !== "" && !queryExactlyMatchesExisting && (
            <ComboboxOption
              key={`create-${query}`}
              value={query}
              className={({ focus }) =>
                classNames(
                  "relative cursor-default select-none py-2 pl-3 pr-9",
                  focus
                    ? "bg-indigo-600 text-slate-50"
                    : "text-slate-900 dark:text-slate-50",
                )
              }
            >
              Create category "{query}"
            </ComboboxOption>
          )}
        </ComboboxOptions>
      </Combobox>
    </div>
  );
}
