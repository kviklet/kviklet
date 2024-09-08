import { useConnections } from "./connection/ConnectionSettings";
import { ChevronUpDownIcon } from "@heroicons/react/20/solid";
import {
  Combobox,
  ComboboxButton,
  ComboboxInput,
  ComboboxOption,
  ComboboxOptions,
  Label,
} from "@headlessui/react";

export default function ConnectionSelector({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  const { connections } = useConnections();
  const options = connections.map((connection) => {
    return { name: connection.displayName, id: connection.id };
  });

  return (
    <ComboBox
      label={""}
      options={options}
      query={value}
      setQuery={onChange}
    ></ComboBox>
  );
}

function classNames(...classes: (string | boolean)[]) {
  return classes.filter(Boolean).join(" ");
}

function ComboBox({
  label,
  options,
  query,
  setQuery,
}: {
  label: string;
  options: { name: string; id: string }[];
  query: string;
  setQuery: (query: string) => void;
}) {
  const escapeRegExp = (str: string) => {
    return str.replace(/[-/\\^$*+?.()|[\]{}]/g, "\\$&");
  };

  const createAntPathMatcher = (pattern: string) => {
    const escapedPattern = escapeRegExp(pattern)
      .replace(/\\\*/g, ".*") // Replace * with .*
      .replace(/\\\?/g, "."); // Replace ? with .
    return new RegExp(`^${escapedPattern}$`, "i");
  };

  const filteredOptions =
    query === ""
      ? options
      : options.filter((option) => {
          const matcher = createAntPathMatcher(query);
          return matcher.test(option.id);
        });

  const onSelect = (option: string | null) => {
    setQuery(option || "");
  };

  return (
    <div>
      <Combobox as="div" value={query} onChange={onSelect}>
        <Label className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50">
          {label}
        </Label>
        <div className="relative">
          <ComboboxInput
            className="w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-slate-900 ring-1 ring-slate-300 focus:outline-none focus:ring-slate-400 dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 focus:dark:ring-slate-500 sm:text-sm sm:leading-6"
            onChange={(event: React.ChangeEvent<HTMLInputElement>) =>
              setQuery(event.target.value)
            }
            displayValue={() => query}
          />
          <ComboboxButton className="absolute inset-y-0 right-0 flex items-center rounded-r-md px-2 focus:outline-none">
            <ChevronUpDownIcon
              className="h-5 w-5 text-slate-400"
              aria-hidden="true"
            />
          </ComboboxButton>

          {filteredOptions.length > 0 && (
            <ComboboxOptions className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-slate-50 py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 sm:text-sm">
              {filteredOptions.map((option) => (
                <ComboboxOption
                  key={option.id}
                  value={option.id}
                  className={({ active }) =>
                    classNames(
                      "relative cursor-default select-none py-2 pl-3 pr-9",
                      active
                        ? "bg-indigo-600 text-slate-50"
                        : "text-slate-900 dark:text-slate-50",
                    )
                  }
                >
                  {() => (
                    <>
                      <span className={classNames("block truncate")}>
                        {option.id}
                      </span>
                    </>
                  )}
                </ComboboxOption>
              ))}
            </ComboboxOptions>
          )}
        </div>
      </Combobox>
      {query !== "" && (
        <span className="my-auto mr-auto mt-1 flex items-center text-sm font-medium text-slate-700 dark:text-slate-200">
          This selector matches&nbsp;
          <span
            className="hover:font-semibold"
            title={
              filteredOptions.length > 0
                ? `Connections matched: ${filteredOptions
                    .map((option) => option.name)
                    .join(", ")}`
                : "No matches"
            }
          >
            {filteredOptions.length} of {options.length} Connections.{" "}
          </span>
        </span>
      )}
    </div>
  );
}
