import { useState } from "react";
import { useConnections } from "./connection/ConnectionSettings";
import {
  ChevronUpDownIcon,
  QuestionMarkCircleIcon,
} from "@heroicons/react/20/solid";
import { Combobox } from "@headlessui/react";

export default function ConnectionSelector() {
  const { connections } = useConnections();
  const options = connections.map((connection) => {
    return { name: connection.displayName, id: connection.id };
  });
  const [selector, setSelector] = useState<string>("");

  return (
    <ComboBox
      label={""}
      options={options}
      query={selector}
      setQuery={setSelector}
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
          return matcher.test(option.name);
        });

  const onSelect = (option: string | null) => {
    console.log(option);
    setQuery(option || "");
  };

  return (
    <div>
      <Combobox as="div" value={query} onChange={onSelect}>
        <Combobox.Label className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50">
          {label}
        </Combobox.Label>
        <div className="relative mt-2">
          <Combobox.Input
            className="w-full rounded-md border-0 bg-slate-50 py-1.5 pl-3 pr-10 text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-inset focus:ring-indigo-600 dark:bg-slate-900 dark:text-slate-50 sm:text-sm sm:leading-6"
            onChange={(event) => setQuery(event.target.value)}
            displayValue={() => query}
          />
          <Combobox.Button className="absolute inset-y-0 right-0 flex items-center rounded-r-md px-2 focus:outline-none">
            <ChevronUpDownIcon
              className="h-5 w-5 text-slate-400"
              aria-hidden="true"
            />
          </Combobox.Button>

          {filteredOptions.length > 0 && (
            <Combobox.Options className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-slate-50 py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 sm:text-sm">
              {filteredOptions.map((option) => (
                <Combobox.Option
                  key={option.id}
                  value={option.name}
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
                        {option.name}
                      </span>
                    </>
                  )}
                </Combobox.Option>
              ))}
            </Combobox.Options>
          )}
        </div>
      </Combobox>
      {query !== "" && (
        <span className="my-auto mr-auto mt-1 flex items-center text-sm font-medium text-slate-700 dark:text-slate-200">
          This selector matches {filteredOptions.length} of {options.length}{" "}
          Connections.
          <QuestionMarkCircleIcon
            className="ml-1 h-4 w-4 text-slate-400"
            title={
              filteredOptions.map((option) => option.name).join(", ") ||
              "No matches"
            }
          />
        </span>
      )}
    </div>
  );
}

function SelectorInfo({
  filteredOptions,
  options,
}: {
  filteredOptions: { name: string; id: string }[];
  options: { name: string; id: string }[];
}) {
  return (
    <div className="flex flex-col">
      <div className="flex w-full justify-between">
        <label className="my-auto mr-auto flex items-center text-sm font-medium text-slate-700 dark:text-slate-200">
          Selected Connection
        </label>
        <input
          type="text"
          className={`block w-full basis-3/5 appearance-none rounded-md border border-slate-300 px-3 
        py-2 text-sm transition-colors focus:border-indigo-600 focus:outline-none
        hover:border-slate-400 focus:hover:border-indigo-600 dark:border-slate-700 dark:bg-slate-900
         dark:focus:border-gray-500 dark:hover:border-slate-600 dark:hover:focus:border-gray-500`}
          autoComplete="new-password"
          value={filteredOptions.length > 0 ? filteredOptions[0].name : ""}
          readOnly
        />
      </div>
      <p className="float-right text-sm text-red-500 dark:text-red-400">
        {filteredOptions.length === 0 && "No matches found"}
      </p>
    </div>
  );
}
