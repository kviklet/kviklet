import {
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
} from "@headlessui/react";
import { useState } from "react";
import { RoleResponse } from "../../api/RoleApi";
import { CheckIcon, ChevronUpDownIcon } from "@heroicons/react/20/solid";

function classNames(...classes: (string | boolean)[]) {
  return classes.filter(Boolean).join(" ");
}

export default function RoleComboBox({
  roles,
  setRoles,
  availableRoles,
}: {
  roles: RoleResponse[];
  setRoles: (roles: RoleResponse[]) => Promise<boolean>;
  availableRoles: RoleResponse[];
}) {
  const [selectedRoles, setSelectedRoles] = useState(roles);

  const onSelectChange = async (roles: RoleResponse[]) => {
    const success = await setRoles(roles);
    if (success) {
      setSelectedRoles(roles);
    }
  };

  return (
    <Listbox
      value={selectedRoles}
      onChange={(roles: RoleResponse[]) => {
        void onSelectChange(roles);
      }}
      multiple
    >
      <div className="relative">
        <ListboxButton
          className="relative cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-slate-900 ring-1 ring-slate-300 focus:outline-none focus:ring-slate-400 dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 focus:dark:ring-slate-500 sm:text-sm sm:leading-6"
          data-testid="role-combobox-button"
        >
          <span className="block w-[8rem] truncate text-left lg:w-[15rem]">
            {selectedRoles.map((role) => role.name).join(", ")}
          </span>
          <span className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2">
            <ChevronUpDownIcon
              className="h-5 w-5 text-slate-400"
              aria-hidden="true"
            />
          </span>
        </ListboxButton>
        <ListboxOptions
          anchor="bottom"
          className="absolute z-10 mt-1 max-h-60 overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 sm:text-sm"
        >
          {availableRoles.map((role) => (
            <ListboxOption
              key={role.id}
              disabled={role.isDefault}
              value={role}
              className="relative cursor-default select-none py-2 pl-3 pr-9 data-[focus]:bg-blue-100 data-[focus]:dark:bg-slate-700 "
              data-testid={`role-combobox-option-${role.name}`}
            >
              {({ selected, focus }) => (
                <>
                  <span
                    className={classNames(
                      selected ? "font-semibold" : "font-normal",
                      "block truncate",
                    )}
                  >
                    {role.name}
                  </span>
                  {selected ? (
                    <span
                      className={classNames(
                        focus ? "text-white" : "text-indigo-600",
                        "absolute inset-y-0 right-0 flex items-center pr-4",
                      )}
                    >
                      <CheckIcon className="h-5 w-5" aria-hidden="true" />
                    </span>
                  ) : null}
                </>
              )}
            </ListboxOption>
          ))}
        </ListboxOptions>
      </div>
    </Listbox>
  );
}
