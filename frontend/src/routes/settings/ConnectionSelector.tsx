import { ChevronUpDownIcon } from "@heroicons/react/20/solid";
import {
  Combobox,
  ComboboxButton,
  ComboboxInput,
  ComboboxOption,
  ComboboxOptions,
} from "@headlessui/react";
import { ChangeEvent } from "react";
import { inputClassName } from "../../components/FormSection";

type SelectableConnection = { id: string; displayName: string };

const escapeRegExp = (value: string) =>
  value.replace(/[-/\\^$*+?.()|[\]{}]/g, "\\$&");

/** The connections a selector applies to: `*` matches any characters, `?` a single one. */
function matchingConnections(
  selector: string,
  connections: SelectableConnection[],
): SelectableConnection[] {
  if (selector === "") {
    return [];
  }
  const pattern = new RegExp(
    `^${escapeRegExp(selector).replace(/\\\*/g, ".*").replace(/\\\?/g, ".")}$`,
    "i",
  );
  return connections.filter((connection) => pattern.test(connection.id));
}

const shownMatches = 4;

function SelectorMatches(props: {
  selector: string;
  connections: SelectableConnection[];
}) {
  // Without the connection list (still loading, or no permission to list connections)
  // there is nothing truthful to say about matches.
  if (props.selector === "" || props.connections.length === 0) {
    return null;
  }
  const matches = matchingConnections(props.selector, props.connections);
  if (matches.length === 0) {
    return (
      <p
        className="mt-1 text-xs text-amber-700 dark:text-amber-400"
        data-testid="connection-selector-matches"
      >
        Matches no connection yet.
      </p>
    );
  }
  if (matches.length === props.connections.length) {
    return (
      <p
        className="mt-1 text-xs text-slate-500 dark:text-slate-400"
        data-testid="connection-selector-matches"
      >
        Matches all {matches.length} connections.
      </p>
    );
  }
  const hidden = matches.length - shownMatches;
  return (
    <p
      className="mt-1 text-xs text-slate-500 dark:text-slate-400"
      data-testid="connection-selector-matches"
    >
      Matches {matches.length} of {props.connections.length} connections:{" "}
      <span className="text-slate-700 dark:text-slate-200">
        {matches
          .slice(0, shownMatches)
          .map((connection) => connection.displayName)
          .join(", ")}
      </span>
      {hidden > 0 && ` and ${hidden} more`}
    </p>
  );
}

/**
 * A connection ID pattern for a role policy. Existing connection IDs are offered as
 * suggestions, and the connections the pattern currently matches are listed below.
 */
export default function ConnectionSelector(props: {
  value: string;
  onChange: (value: string) => void;
  connections: SelectableConnection[];
  error?: string;
}) {
  const { value, connections } = props;
  // A pattern suggests what it matches; plain text suggests the connections containing it.
  const isPattern = /[*?]/.test(value);
  const suggestions = isPattern
    ? matchingConnections(value, connections)
    : connections.filter(
        (connection) =>
          connection.id.toLowerCase().includes(value.toLowerCase()) ||
          connection.displayName.toLowerCase().includes(value.toLowerCase()),
      );

  return (
    <div className="min-w-0">
      <Combobox
        as="div"
        value={value}
        onChange={(selected: string | null) => props.onChange(selected ?? "")}
      >
        <div className="relative">
          <ComboboxInput
            aria-label="Connection ID or pattern"
            data-testid="connection-selector-input"
            className={`${inputClassName} pr-10 font-mono`}
            placeholder="orders-* or a connection ID"
            onChange={(event: ChangeEvent<HTMLInputElement>) =>
              props.onChange(event.target.value)
            }
            displayValue={() => value}
          />
          <ComboboxButton className="absolute inset-y-0 right-0 flex items-center rounded-r-md px-2 focus:outline-none">
            <ChevronUpDownIcon
              className="h-5 w-5 text-slate-400"
              aria-hidden="true"
            />
          </ComboboxButton>

          {suggestions.length > 0 && (
            <ComboboxOptions className="absolute z-10 mt-1 max-h-60 w-full overflow-auto rounded-md bg-slate-50 py-1 text-sm shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 dark:shadow-none dark:ring-slate-700">
              {suggestions.map((connection) => (
                <ComboboxOption
                  key={connection.id}
                  value={connection.id}
                  className={({ focus }) =>
                    `relative flex cursor-default select-none items-baseline justify-between gap-3 px-3 py-2 ${
                      focus
                        ? "bg-indigo-600 text-slate-50"
                        : "text-slate-900 dark:text-slate-50"
                    }`
                  }
                >
                  <span className="truncate font-mono">{connection.id}</span>
                  <span className="truncate text-xs opacity-70">
                    {connection.displayName}
                  </span>
                </ComboboxOption>
              ))}
            </ComboboxOptions>
          )}
        </div>
      </Combobox>
      {props.error ? (
        <p className="mt-1 text-sm text-red-600 dark:text-red-400">
          {props.error}
        </p>
      ) : (
        <SelectorMatches selector={value} connections={connections} />
      )}
    </div>
  );
}

export { matchingConnections };
export type { SelectableConnection };
