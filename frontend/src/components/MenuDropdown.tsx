import { Fragment } from "react";
import {
  Menu,
  MenuButton,
  MenuItem,
  MenuItems,
  Transition,
} from "@headlessui/react";
import { EllipsisVerticalIcon } from "@heroicons/react/20/solid";

interface MenuItem {
  onClick: () => void;
  content: string | JSX.Element;
  enabled: boolean;
  tooltip?: string;
}

interface MenuDropDownProps {
  items: MenuItem[];
}

export default function MenuDropDown(props: MenuDropDownProps) {
  const defaultButtonClasses =
    "bg-slate-100 text-slate-900 dark:bg-slate-800 dark:text-slate-50 block w-full px-4 py-2 text-left text-sm" +
    "hover:bg-slate-100 hover:text-slate-900 dark:hover:bg-slate-700 dark:hover:text-slate-50";

  const disabledButtonStyles =
    "bg-slate-200 text-slate-400 dark:bg-slate-800 dark:text-slate-400 block w-full px-4 py-2 text-left text-sm cursor-not-allowed";

  return (
    <Menu as="div" className="mx-2">
      <MenuButton
        className="
        flex items-center justify-center rounded-md bg-slate-100 px-1 py-2 align-middle text-base font-semibold leading-5 text-slate-900 transition-colors hover:bg-sky-900 hover:bg-slate-100 hover:text-slate-900 dark:bg-slate-800 dark:text-slate-50 dark:shadow-sm dark:hover:bg-slate-700 dark:hover:text-slate-50"
      >
        <span className="sr-only">Open options</span>
        <EllipsisVerticalIcon className="m-auto h-6" aria-hidden="true" />
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
        <MenuItems className="absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-slate-50 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-800">
          <div className="py-1">
            {props.items.map((item) => (
              <MenuItem>
                {() => (
                  <button
                    onClick={item.onClick}
                    title={item.tooltip}
                    className={
                      item.enabled ? defaultButtonClasses : disabledButtonStyles
                    }
                  >
                    {item.content}
                  </button>
                )}
              </MenuItem>
            ))}
          </div>
        </MenuItems>
      </Transition>
    </Menu>
  );
}
