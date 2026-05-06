import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import {
  ConfigPayload,
  ConfigPayloadSchema,
  ConfigResponse,
} from "../../api/ConfigApi";
import { getRoles, RoleResponse } from "../../api/RoleApi";
import InputField from "../../components/InputField";
import Button from "../../components/Button";
import useConfig from "../../components/ConfigProvider";
import Spinner from "../../components/Spinner";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
  Listbox,
  ListboxButton,
  ListboxOption,
  ListboxOptions,
} from "@headlessui/react";
import {
  CheckIcon,
  ChevronDownIcon,
  ChevronRightIcon,
  ChevronUpDownIcon,
} from "@heroicons/react/20/solid";

export default function GeneralSettings() {
  const { config, loading, updateConfig } = useConfig();

  return (
    <div>
      <h1 className="mb-2 text-lg">General Settings</h1>
      {loading ? (
        <Spinner size="lg" />
      ) : (
        config && <ConfigForm config={config} onSubmit={updateConfig} />
      )}
    </div>
  );
}

const ConfigForm = ({
  config,
  onSubmit,
}: {
  config: ConfigResponse;
  onSubmit: (data: ConfigPayload) => Promise<void>;
}) => {
  const [allRoles, setAllRoles] = useState<RoleResponse[]>([]);

  useEffect(() => {
    void getRoles().then((res) => {
      if ("roles" in res) setAllRoles(res.roles);
    });
  }, []);
  const {
    register,
    handleSubmit,
    control,
    formState: { errors },
  } = useForm<ConfigPayload>({
    resolver: zodResolver(ConfigPayloadSchema),
    defaultValues: {
      teamsUrl: config.teamsUrl,
      slackUrl: config.slackUrl,
      newUserRoleIds: config.newUserRoleIds ?? [],
    },
  });

  return (
    <form
      className="divide-y divide-slate-200 dark:divide-slate-700"
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
    >
      <Disclosure defaultOpen={true}>
        {({ open }) => (
          <div className="py-4">
            <DisclosureButton className="block w-full">
              <div className="flex flex-row items-center justify-between">
                <h2>Notification Settings</h2>
                {open ? (
                  <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500" />
                ) : (
                  <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500" />
                )}
              </div>
            </DisclosureButton>
            <DisclosurePanel unmount={false} className="mt-4">
              <div className="flex flex-col space-y-4">
                <span className="text-sm dark:text-slate-300">
                  See the Readme section on Notifications to see how to add
                  webhooks, to get notified in a channel when reviews are
                  necessary.
                </span>
                <InputField
                  label="Teams Webhook URL"
                  type="text"
                  {...register("teamsUrl")}
                  placeholder="Teams URL"
                  error={errors.teamsUrl}
                />
                <InputField
                  label="Slack Webhook URL"
                  type="text"
                  {...register("slackUrl")}
                  placeholder="Slack URL"
                  error={errors.slackUrl}
                />
              </div>
            </DisclosurePanel>
          </div>
        )}
      </Disclosure>

      <Disclosure defaultOpen={true}>
        {({ open }) => (
          <div className="py-4">
            <DisclosureButton className="block w-full">
              <div className="flex flex-row items-center justify-between">
                <h2>New User Settings</h2>
                {open ? (
                  <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500" />
                ) : (
                  <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500" />
                )}
              </div>
            </DisclosureButton>
            <DisclosurePanel unmount={false} className="mt-4">
              <div className="flex flex-col space-y-4">
                <span className="text-sm dark:text-slate-300">
                  Roles assigned to every new user upon registration, in
                  addition to the Default role.
                </span>
                <Controller
                  name="newUserRoleIds"
                  control={control}
                  render={({ field }) => {
                    const selectedRoles = allRoles.filter((r) =>
                      (field.value ?? []).includes(r.id),
                    );
                    return (
                      <div>
                        <label className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-300">
                          New User Roles
                        </label>
                        <Listbox
                          value={selectedRoles}
                          onChange={(roles) =>
                            field.onChange(roles.map((r) => r.id))
                          }
                          multiple
                        >
                          <div className="relative">
                            <ListboxButton className="relative w-64 cursor-default rounded-md bg-white py-1.5 pl-3 pr-10 text-left text-slate-900 ring-1 ring-slate-300 focus:outline-none focus:ring-slate-400 dark:bg-slate-900 dark:text-slate-50 dark:ring-slate-700 sm:text-sm sm:leading-6">
                              <span className="block truncate">
                                {selectedRoles.length === 0
                                  ? "None"
                                  : selectedRoles.map((r) => r.name).join(", ")}
                              </span>
                              <span className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2">
                                <ChevronUpDownIcon className="h-5 w-5 text-slate-400" />
                              </span>
                            </ListboxButton>
                            <ListboxOptions
                              anchor="bottom"
                              className="absolute z-10 mt-1 max-h-60 w-64 overflow-auto rounded-md bg-white py-1 text-base shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none dark:bg-slate-900 dark:text-slate-50 sm:text-sm"
                            >
                              {allRoles
                                .filter((r) => !r.isDefault)
                                .map((role) => (
                                  <ListboxOption
                                    key={role.id}
                                    value={role}
                                    className="relative cursor-default select-none py-2 pl-3 pr-9 data-[focus]:bg-blue-100 data-[focus]:dark:bg-slate-700"
                                  >
                                    {({ selected }) => (
                                      <>
                                        <span
                                          className={
                                            selected
                                              ? "font-semibold"
                                              : "font-normal"
                                          }
                                        >
                                          {role.name}
                                        </span>
                                        {selected && (
                                          <span className="absolute inset-y-0 right-0 flex items-center pr-4 text-indigo-600">
                                            <CheckIcon className="h-5 w-5" />
                                          </span>
                                        )}
                                      </>
                                    )}
                                  </ListboxOption>
                                ))}
                            </ListboxOptions>
                          </div>
                        </Listbox>
                      </div>
                    );
                  }}
                />
              </div>
            </DisclosurePanel>
          </div>
        )}
      </Disclosure>

      <div className="flex flex-row-reverse pt-4">
        <Button htmlType="submit" variant="primary">
          Save
        </Button>
      </div>
    </form>
  );
};
