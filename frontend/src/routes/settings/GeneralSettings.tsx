import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import {
  ConfigPayload,
  ConfigPayloadSchema,
  ConfigResponse,
} from "../../api/ConfigApi";
import InputField from "../../components/InputField";
import Button from "../../components/Button";
import useConfig from "../../hooks/config";
import Spinner from "../../components/Spinner";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
} from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";

export default function GeneralSettings() {
  const { config, loading, updateConfig } = useConfig();

  const onSubmit = async (data: ConfigPayload) => {
    await updateConfig(data);
  };

  return (
    <div>
      <h1 className="mb-2 text-lg">General Settings</h1>
      {loading ? (
        <Spinner />
      ) : (
        config && (
          <div>
            <Disclosure defaultOpen={true}>
              {({ open }) => (
                <>
                  <DisclosureButton className="py-2">
                    <div className="flex flex-row justify-between">
                      <div className="flex flex-row">
                        <h2>Notification Settings</h2>
                      </div>
                      <div className="flex flex-row">
                        {open ? (
                          <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
                        ) : (
                          <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
                        )}
                      </div>
                    </div>
                  </DisclosureButton>
                  <DisclosurePanel unmount={false}>
                    <ConfigForm
                      config={config}
                      onSubmit={onSubmit}
                    ></ConfigForm>
                  </DisclosurePanel>
                </>
              )}
            </Disclosure>
          </div>
        )
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
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ConfigPayload>({
    resolver: zodResolver(ConfigPayloadSchema),
    defaultValues: {
      teamsUrl: config?.teamsUrl,
      slackUrl: config?.slackUrl,
      liveSessionEnabled: config?.liveSessionEnabled || false,
    },
  });

  return (
    <form
      className="flex flex-col space-y-4"
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
    >
      <span className="text-sm dark:text-slate-300">
        See the Readme section on Notifications to see how to add webhooks, to
        get notified in a channel when reviews are necessary.
      </span>
      <InputField
        label="Teams Webhook URL"
        type="text"
        {...register("teamsUrl")}
        placeholder="Teams URL"
        error={errors.teamsUrl}
      ></InputField>
      <InputField
        label="Slack Webhook URL"
        type="text"
        {...register("slackUrl")}
        placeholder="Slack URL"
        error={errors.slackUrl}
      ></InputField>
      <span className="text-sm dark:text-slate-300">Beta Features</span>
      <InputField
        label="Live Session Enabled (Beta)"
        type="checkbox"
        {...register("liveSessionEnabled")}
        error={errors.liveSessionEnabled}
      ></InputField>
      <div className="flex flex-row-reverse">
        <Button type="submit">Save</Button>
      </div>
    </form>
  );
};
