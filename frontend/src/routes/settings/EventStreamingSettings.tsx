// This file is not MIT licensed
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  EventStreamingSettings as Settings,
  EventStreamingSettingsSchema,
  EventStreamingResponse,
  getEventStreaming,
  putEventStreaming,
} from "../../api/EventStreamingApi";
import { isApiErrorResponse } from "../../api/Errors";
import useConfig from "../../components/ConfigProvider";
import { useHasPermission } from "../../hooks/permissions";
import Button from "../../components/Button";
import InputField from "../../components/InputField";
import ReadOnlyNotice from "../../components/ReadOnlyNotice";

export default function EventStreamingSettings() {
  const { config } = useConfig();
  const canEdit = useHasPermission("configuration:edit");
  const [data, setData] = useState<EventStreamingResponse | null>(null);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const {
    register,
    reset,
    handleSubmit,
    watch,
    setError: setFieldError,
    formState: { errors },
  } = useForm<Settings>({
    resolver: zodResolver(EventStreamingSettingsSchema),
  });

  useEffect(() => {
    let mounted = true;
    void getEventStreaming().then((response) => {
      if (!mounted) return;
      if (isApiErrorResponse(response)) setError(response.message);
      else {
        setData(response);
        reset(response.settings);
      }
    });
    return () => {
      mounted = false;
    };
  }, [reset]);

  const save = async (settings: Settings) => {
    if (settings.maxArchiveSizeMiB < settings.maxFileSizeMiB) {
      setFieldError("maxArchiveSizeMiB", {
        message: "The archive budget must cover at least one rotated file.",
      });
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    const response = await putEventStreaming(settings);
    if (isApiErrorResponse(response)) setError(response.message);
    else {
      reset(response.settings);
      setData(response);
      setMessage("Settings saved.");
      const current = await getEventStreaming();
      if (!isApiErrorResponse(current)) setData(current);
    }
    setSaving(false);
  };

  const directoryChanged =
    data && watch("directory") !== data.settings.directory;
  const licenseValid = config?.licenseValid === true;
  return (
    <div className="max-w-3xl space-y-4">
      <div className="flex items-center gap-2">
        <h1 className="text-lg">Event Log Streaming</h1>
        <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs text-purple-800 dark:bg-purple-900 dark:text-purple-200">
          Enterprise
        </span>
      </div>
      <p className="text-sm">
        Write security activities to JSON Lines files for your SIEM or
        monitoring agent to collect.
      </p>
      {!licenseValid && (
        <p className="text-sm">
          A valid Enterprise license is required to enable streaming.{" "}
          <Link
            className="text-blue-600 underline dark:text-blue-400"
            to="/settings/license"
          >
            Manage license
          </Link>
          . After renewal, save with streaming enabled to resume.
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-400">
          {error}
        </p>
      )}
      {message && (
        <p role="status" className="text-sm">
          {message}
        </p>
      )}
      {data && (
        <>
          <div
            className="rounded border border-slate-300 p-3 text-sm dark:border-slate-700"
            aria-label="Stream status"
          >
            <p>
              Status: <strong>{data.status.state.replaceAll("_", " ")}</strong>
            </p>
            <p>
              Last successful write:{" "}
              {data.status.lastWriteAt
                ? new Date(data.status.lastWriteAt).toLocaleString()
                : "No events written in this process"}
            </p>
            <p>
              Detected failures since restart: {data.status.detectedFailures}
            </p>
            {data.status.lastError && (
              <p role="alert">{data.status.lastError}</p>
            )}
            <Button
              className="mt-2"
              onClick={() => {
                void getEventStreaming().then((response) => {
                  if (isApiErrorResponse(response)) setError(response.message);
                  else setData(response);
                });
              }}
            >
              Refresh status
            </Button>
          </div>
          {!canEdit && <ReadOnlyNotice resource="these settings" />}
          <form
            onSubmit={(event) => void handleSubmit(save)(event)}
            className="space-y-4"
          >
            <fieldset disabled={!canEdit || saving} className="space-y-4">
              <label className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  {...register("enabled")}
                  disabled={
                    !canEdit ||
                    saving ||
                    (!licenseValid && !data.settings.enabled)
                  }
                />
                Enable event file output
              </label>
              <InputField
                label="Output directory (absolute path)"
                id="event-stream-directory"
                {...register("directory")}
                error={errors.directory?.message}
              />
              {directoryChanged && (
                <p className="text-sm text-amber-700 dark:text-amber-400">
                  Changing the directory leaves existing files in the previous
                  directory. Move or remove them separately and update your
                  collector.
                </p>
              )}
              <InputField
                label="Maximum file size (MiB)"
                type="number"
                min={1}
                max={1024}
                id="event-stream-maxFileSizeMiB"
                {...register("maxFileSizeMiB", { valueAsNumber: true })}
                error={errors.maxFileSizeMiB?.message}
              />
              <InputField
                label="Retention (days)"
                type="number"
                min={1}
                max={365}
                id="event-stream-retentionDays"
                {...register("retentionDays", { valueAsNumber: true })}
                error={errors.retentionDays?.message}
              />
              <InputField
                label="Maximum archive size (MiB)"
                type="number"
                min={1}
                max={102400}
                id="event-stream-maxArchiveSizeMiB"
                {...register("maxArchiveSizeMiB", { valueAsNumber: true })}
                error={errors.maxArchiveSizeMiB?.message}
              />
            </fieldset>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              Files rotate at midnight UTC or when they reach the size
              threshold. Archive limits exclude the active file; cleanup is
              asynchronous. Mount persistent storage when running in Docker or
              Kubernetes and use one writer per directory.
            </p>
            <p className="text-sm text-slate-600 dark:text-slate-400">
              SQL statements and commands are included and may contain sensitive
              literals. Returned data, passwords and tokens are excluded. File
              output is best effort: failed writes are counted and are not
              replayed. Proxy queries include session details; their result
              counts and outcome are unknown.
            </p>
            {canEdit && (
              <Button
                htmlType="submit"
                variant={saving ? "disabled" : "primary"}
              >
                {saving ? "Saving…" : "Save"}
              </Button>
            )}
          </form>
        </>
      )}
    </div>
  );
}
