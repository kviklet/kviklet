import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import {
  ConfigPayload,
  ConfigPayloadSchema,
  ConfigResponse,
} from "../../api/ConfigApi";
import InputField from "../../components/InputField";
import Button from "../../components/Button";
import useConfig from "../../components/ConfigProvider";
import Spinner from "../../components/Spinner";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
} from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";

export default function GeneralSettings() {
  const { config, loading, updateConfig, refreshConfig } = useConfig();

  // Refresh in the background when the page opens so the webhook URLs reflect the
  // latest server state even if the initial app-load fetch happened while logged out.
  useEffect(() => {
    void refreshConfig(true);
  }, []);

  const onSubmit = async (data: ConfigPayload) => {
    await updateConfig(data);
  };

  return (
    <div>
      <h1 className="mb-2 text-lg">General Settings</h1>
      {loading ? (
        <Spinner size="lg" page />
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
    reset,
    formState: { errors, isDirty },
  } = useForm<ConfigPayload>({
    resolver: zodResolver(ConfigPayloadSchema),
    defaultValues: {
      teamsUrl: config?.teamsUrl,
      slackUrl: config?.slackUrl,
    },
  });

  // When a background refresh brings in newer config, sync it into the form — but
  // never overwrite edits the user has already started (isDirty guard).
  useEffect(() => {
    if (!isDirty) {
      reset({
        teamsUrl: config?.teamsUrl ?? "",
        slackUrl: config?.slackUrl ?? "",
      });
    }
  }, [config?.teamsUrl, config?.slackUrl]);

  return (
    <form
      className="flex flex-col space-y-4"
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
    >
      <span className="text-sm dark:text-slate-300">
        See the{" "}
        <a
          href="https://github.com/kviklet/kviklet/blob/main/Readme.md#notifications"
          target="_blank"
          rel="noreferrer"
          className="text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
        >
          Readme section on Notifications
        </a>{" "}
        to see how to add webhooks, to get notified in a channel when reviews
        are necessary.
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
      <div className="flex flex-row-reverse">
        <Button htmlType="submit" variant="primary">
          Save
        </Button>
      </div>
    </form>
  );
};
