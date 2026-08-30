import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import {
  ConfigPayload,
  ConfigPayloadSchema,
  ConfigResponse,
} from "../../api/ConfigApi";
import InputField from "../../components/InputField";
import Button from "../../components/Button";
import Toggle from "../../components/Toggle";
import useConfig from "../../components/ConfigProvider";
import Spinner from "../../components/Spinner";
import {
  Disclosure,
  DisclosureButton,
  DisclosurePanel,
} from "@headlessui/react";
import { ChevronDownIcon, ChevronRightIcon } from "@heroicons/react/20/solid";
import { useHasPermission } from "../../hooks/permissions";
import ReadOnlyNotice from "../../components/ReadOnlyNotice";

export default function GeneralSettings() {
  const { config, loading, updateConfig, refreshConfig } = useConfig();
  const canEditConfig = useHasPermission("configuration:edit");

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
          <ConfigForm
            config={config}
            onSubmit={onSubmit}
            readOnly={!canEditConfig}
          ></ConfigForm>
        )
      )}
    </div>
  );
}

const SectionHeader = ({
  title,
  open,
  badge,
}: {
  title: string;
  open: boolean;
  badge?: string;
}) => (
  <div className="flex flex-row justify-between">
    <div className="flex flex-row items-center gap-2">
      <h2>{title}</h2>
      {badge && (
        <span
          className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium
                     text-purple-800 dark:bg-purple-900 dark:text-purple-200"
        >
          {badge}
        </span>
      )}
    </div>
    <div className="flex flex-row">
      {open ? (
        <ChevronDownIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronDownIcon>
      ) : (
        <ChevronRightIcon className="h-6 w-6 text-slate-400 dark:text-slate-500"></ChevronRightIcon>
      )}
    </div>
  </div>
);

const ConfigForm = ({
  config,
  onSubmit,
  readOnly = false,
}: {
  config: ConfigResponse;
  onSubmit: (data: ConfigPayload) => Promise<void>;
  /** Renders the form as a pure viewer: no Save button, all inputs disabled. */
  readOnly?: boolean;
}) => {
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    watch,
    formState: { errors, isDirty },
  } = useForm<ConfigPayload>({
    resolver: zodResolver(ConfigPayloadSchema),
    defaultValues: {
      teamsUrl: config?.teamsUrl,
      slackUrl: config?.slackUrl,
      proxyEnabled: config.proxyEnabled ?? false,
    },
  });

  // When a background refresh brings in newer config, sync it into the form — but
  // never overwrite edits the user has already started (isDirty guard).
  useEffect(() => {
    if (!isDirty) {
      reset({
        teamsUrl: config?.teamsUrl ?? "",
        slackUrl: config?.slackUrl ?? "",
        proxyEnabled: config.proxyEnabled ?? false,
      });
    }
  }, [config?.teamsUrl, config?.slackUrl, config.proxyEnabled]);

  const proxyEnabled = watch("proxyEnabled") ?? false;
  const licenseValid = config.licenseValid;
  // With an expired license the toggle stays usable in one direction: an admin can
  // still switch a running proxy off, just not turn it (back) on. The gate looks at
  // the saved value so an unsaved switch to off can still be undone before saving.
  const lockedByLicense = !licenseValid && !(config.proxyEnabled ?? false);

  return (
    <form
      className="flex flex-col"
      onSubmit={(event) => void handleSubmit(onSubmit)(event)}
    >
      {readOnly && (
        <ReadOnlyNotice resource="these settings" className="mb-2" />
      )}
      <Disclosure defaultOpen={true}>
        {({ open }) => (
          <>
            <DisclosureButton className="py-2" type="button">
              <SectionHeader title="Notification Settings" open={open} />
            </DisclosureButton>
            <DisclosurePanel unmount={false}>
              <fieldset disabled={readOnly} className="flex flex-col space-y-4">
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
                  to see how to add webhooks, to get notified in a channel when
                  reviews are necessary.
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
              </fieldset>
            </DisclosurePanel>
          </>
        )}
      </Disclosure>
      <Disclosure defaultOpen={true}>
        {({ open }) => (
          <>
            <DisclosureButton className="py-2" type="button">
              <SectionHeader
                title="Database Proxy"
                open={open}
                badge="Enterprise"
              />
            </DisclosureButton>
            <DisclosurePanel unmount={false}>
              <div className="flex flex-col space-y-4">
                <span className="text-sm dark:text-slate-300">
                  Lets users connect native clients like psql or DataGrip
                  through Kviklet, with every statement recorded in the audit
                  log.
                </span>
                <div className="flex flex-row items-center justify-between">
                  <span className="text-sm">
                    Enable the proxy for approved requests
                  </span>
                  <Toggle
                    active={proxyEnabled}
                    disabled={readOnly || lockedByLicense}
                    tooltip={
                      readOnly
                        ? "You lack the permission to change this setting"
                        : lockedByLicense
                        ? "The database proxy requires an enterprise license"
                        : undefined
                    }
                    onClick={() =>
                      setValue("proxyEnabled", !proxyEnabled, {
                        shouldDirty: true,
                      })
                    }
                  />
                </div>
                {!licenseValid && (
                  <span className="text-sm text-slate-500 dark:text-slate-400">
                    The database proxy is an enterprise feature.{" "}
                    <a
                      href="https://kviklet.dev"
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      Get a license at kviklet.dev
                    </a>{" "}
                    or{" "}
                    <Link
                      to="/settings/license"
                      className="text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      upload one here
                    </Link>
                    .
                  </span>
                )}
              </div>
            </DisclosurePanel>
          </>
        )}
      </Disclosure>
      {!readOnly && (
        <div className="mt-4 flex flex-row-reverse">
          <Button htmlType="submit" variant="primary">
            Save
          </Button>
        </div>
      )}
    </form>
  );
};
