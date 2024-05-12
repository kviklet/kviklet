import { Fragment } from "react";
import { Menu, Transition } from "@headlessui/react";
import { EllipsisVerticalIcon } from "@heroicons/react/20/solid";

function classNames(...classes: (string | false | null | undefined)[]) {
  return classes.filter(Boolean).join(" ");
}

interface MenuItem {
  onClick: () => void;
  text: string;
  enabled: boolean;
}

interface MenuDropDownProps {
  items: MenuItem[];
}

export default function MenuDropDown(props: MenuDropDownProps) {
  return (
    <Menu as="div" className="mx-2">
      <Menu.Button
        className="
        h-10 w-8 rounded border
        border-gray-300 text-center transition-colors
         hover:border-gray-400 
         dark:border-slate-700 dark:bg-slate-800 dark:text-slate-50
         dark:hover:border-slate-500"
      >
        <span className="sr-only">Open options</span>
        <EllipsisVerticalIcon className="m-auto h-6" aria-hidden="true" />
      </Menu.Button>

      <Transition
        as={Fragment}
        enter="transition ease-out duration-100"
        enterFrom="transform opacity-0 scale-95"
        enterTo="transform opacity-100 scale-100"
        leave="transition ease-in duration-75"
        leaveFrom="transform opacity-100 scale-100"
        leaveTo="transform opacity-0 scale-95"
      >
        <Menu.Items className="absolute right-0 z-10 mt-2 w-56 origin-top-right rounded-md bg-slate-50 shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-800">
          <div className="py-1">
            {props.items.map((item) => (
              <Menu.Item key={item.text}>
                {({ active }) => (
                  <button
                    onClick={item.onClick}
                    className={classNames(
                      active ? "bg-slate-100 text-slate-900" : "text-slate-700",
                      "dark:bg-slate-800 dark:text-slate-50",
                      "block w-full px-4 py-2 text-left text-sm",
                    )}
                  >
                    {item.text}
                  </button>
                )}
              </Menu.Item>
            ))}
          </div>
        </Menu.Items>
      </Transition>
    </Menu>
  );
}
