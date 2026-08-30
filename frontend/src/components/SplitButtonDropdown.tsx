import { Fragment, ReactElement } from "react";
import {
  Menu,
  MenuButton,
  MenuItem,
  MenuItems,
  Transition,
} from "@headlessui/react";
import { ChevronDownIcon } from "@heroicons/react/20/solid";

interface SplitButtonMenuItem {
  onClick: () => void;
  content: string | ReactElement;
  /** Muted one-liner under the label, GitHub merge-menu style. */
  description?: string;
  enabled: boolean;
  tooltip?: string;
}

// The dropdown half of a GitHub-style split button: render it directly to the right of the
// primary action button, which should drop its right rounding (rounded-r-none). The chevron
// mirrors the primary button's variant but stays clickable even when that is disabled, so
// the menu can explain each entry through its own enabled state and tooltip.
export default function SplitButtonDropdown(props: {
  items: SplitButtonMenuItem[];
  variant: "primary" | "disabled";
  dataTestId?: string;
}) {
  const chevronStyles =
    props.variant === "primary"
      ? "border-l border-indigo-900/40 bg-indigo-700 text-white hover:bg-indigo-800 dark:border-indigo-950 dark:bg-indigo-700 dark:hover:bg-indigo-600"
      : "border-l border-slate-400 bg-slate-300 text-slate-500 dark:border-slate-600 dark:bg-slate-700 dark:text-slate-500";

  const defaultButtonClasses =
    "block w-full rounded px-3 py-2 text-left hover:bg-slate-100 dark:hover:bg-slate-800";

  const disabledButtonStyles =
    "block w-full rounded px-3 py-2 text-left cursor-not-allowed";

  return (
    <Menu as="div" className="relative flex">
      <MenuButton
        className={`flex items-center rounded-r px-0.5 transition-colors ${chevronStyles}`}
        data-testid={props.dataTestId}
      >
        <span className="sr-only">More execution options</span>
        <ChevronDownIcon className="h-5 w-5" aria-hidden="true" />
      </MenuButton>

      <Transition
        as={Fragment}
        enter="transition ease-out duration-100"
        enterFrom="transform opacity-0 scale-95"
        enterTo="transform opacity-100 scale-100"
        leave="transition ease-in duration-75"
        leaveFrom="transform opacity-100 scale-100"
        leaveTo="transform opacity-0 scale-95"
      >
        <MenuItems className="absolute right-0 top-full z-10 mt-2 w-72 origin-top-right rounded-md border border-slate-200 bg-white p-1 shadow-lg focus:outline-none dark:border-slate-700 dark:bg-slate-900">
          {props.items.map((item, index) => (
            <MenuItem key={index}>
              {() => (
                <button
                  onClick={item.onClick}
                  disabled={!item.enabled}
                  title={item.tooltip}
                  className={
                    item.enabled ? defaultButtonClasses : disabledButtonStyles
                  }
                >
                  <span
                    className={`block text-sm font-medium ${
                      item.enabled
                        ? "text-slate-900 dark:text-slate-50"
                        : "text-slate-400 dark:text-slate-500"
                    }`}
                  >
                    {item.content}
                  </span>
                  {item.description && (
                    <span
                      className={`block text-xs ${
                        item.enabled
                          ? "text-slate-500 dark:text-slate-400"
                          : "text-slate-400 dark:text-slate-600"
                      }`}
                    >
                      {item.description}
                    </span>
                  )}
                </button>
              )}
            </MenuItem>
          ))}
        </MenuItems>
      </Transition>
    </Menu>
  );
}
