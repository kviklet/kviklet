import { CircleStackIcon, CommandLineIcon } from "@heroicons/react/24/outline";
import { ConnectionKind, databaseTypes } from "./connectionForm";

const kinds: { value: ConnectionKind; label: string; beta?: boolean }[] = [
  ...databaseTypes,
  { value: "KUBERNETES", label: "Kubernetes", beta: true },
];

/** The first decision of a new connection: what it connects to. */
export default function ConnectionTypePicker(props: {
  value: ConnectionKind;
  onChange: (kind: ConnectionKind) => void;
}) {
  return (
    <fieldset>
      <legend className="sr-only">Connection type</legend>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
        {kinds.map((kind) => {
          const selected = props.value === kind.value;
          const Icon =
            kind.value === "KUBERNETES" ? CommandLineIcon : CircleStackIcon;
          return (
            <label
              key={kind.value}
              data-testid={`connection-type-${kind.value}`}
              className={`relative flex cursor-pointer flex-col items-center gap-2 rounded-lg border px-3 py-4 text-sm font-medium transition-colors focus-within:ring-2 focus-within:ring-indigo-600 ${
                selected
                  ? "border-indigo-600 bg-indigo-50 text-indigo-900 dark:border-indigo-400 dark:bg-indigo-950 dark:text-indigo-100"
                  : "border-slate-200 bg-white text-slate-700 hover:border-slate-400 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200 dark:hover:border-slate-500"
              }`}
            >
              <input
                type="radio"
                name="connection-kind"
                className="sr-only"
                value={kind.value}
                checked={selected}
                onChange={() => props.onChange(kind.value)}
              />
              <Icon
                className={`h-6 w-6 ${
                  selected
                    ? "text-indigo-600 dark:text-indigo-300"
                    : "text-slate-400 dark:text-slate-500"
                }`}
                aria-hidden="true"
              />
              {kind.label}
              {kind.beta && (
                <span className="absolute right-1.5 top-1.5 rounded-full bg-amber-100 px-1.5 py-0.5 text-[10px] font-medium leading-none text-amber-700 dark:bg-amber-900 dark:text-amber-300">
                  Beta
                </span>
              )}
            </label>
          );
        })}
      </div>
    </fieldset>
  );
}
