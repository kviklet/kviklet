// This file is not MIT licensed
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import {
  EventStreamingSettings as Settings,
  EventStreamingResponse,
  getEventStreaming,
} from "../../api/EventStreamingApi";
import { isApiErrorResponse } from "../../api/Errors";
import useConfig from "../../components/ConfigProvider";
import Button from "../../components/Button";

const levelDescriptions: Record<Settings["loggingLevel"], string> = {
  SECURITY_ONLY:
    "Authentication, permission denials, user and role changes, API keys, and security configuration changes. Routine request, review, proxy session, and execution activity is omitted.",
  WITHOUT_QUERY_TEXT:
    "All covered events, including access reasons, approvals, proxy activity, and execution metadata. SQL statements and Kubernetes commands are omitted. Access reasons may still contain query text pasted by users.",
  FULL: "All covered events, including access reasons, SQL statements, Kubernetes commands, and execution metadata. These text fields may contain sensitive information.",
};

const levelLabels: Record<Settings["loggingLevel"], string> = {
  SECURITY_ONLY: "Level 1 — Security events only",
  WITHOUT_QUERY_TEXT: "Level 2 — All events, without query text",
  FULL: "Level 3 — All events, with query text",
};

export default function EventStreamingSettings() {
  const { config } = useConfig();
  const [data, setData] = useState<EventStreamingResponse | null>(null);
  const [error, setError] = useState("");
  const docsRef =
    config?.gitCommit && /^[a-f0-9]{7,40}$/i.test(config.gitCommit)
      ? config.gitCommit
      : "main";
  const docsUrl = `https://github.com/kviklet/kviklet/blob/${docsRef}/docs/event-streaming/README.md`;

  useEffect(() => {
    let mounted = true;
    void getEventStreaming().then((response) => {
      if (!mounted) return;
      if (isApiErrorResponse(response)) setError(response.message);
      else setData(response);
    });
    return () => {
      mounted = false;
    };
  }, []);

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
      <p className="text-sm">
        Managed by deployment configuration. Changes require an application
        restart.{" "}
        <a
          href={docsUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="text-blue-600 underline dark:text-blue-400"
        >
          View setup instructions
        </a>
      </p>
      {config?.licenseValid === false && (
        <p className="text-sm">
          A valid Enterprise license is required for streaming.{" "}
          <Link
            className="text-blue-600 underline dark:text-blue-400"
            to="/settings/license"
          >
            Manage license
          </Link>
          . A stream enabled in deployment configuration resumes automatically
          after renewal.
        </p>
      )}
      {error && (
        <p role="alert" className="text-sm text-red-700 dark:text-red-400">
          {error}
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
                  else {
                    setError("");
                    setData(response);
                  }
                });
              }}
            >
              Refresh status
            </Button>
          </div>
          <dl className="space-y-3 text-sm">
            <div>
              <dt className="font-medium">Configured enablement</dt>
              <dd>{data.settings.enabled ? "Enabled" : "Disabled"}</dd>
            </div>
            <div>
              <dt className="font-medium">Logging level</dt>
              <dd>{levelLabels[data.settings.loggingLevel]}</dd>
              <dd className="mt-1 text-slate-600 dark:text-slate-400">
                {levelDescriptions[data.settings.loggingLevel]}
              </dd>
            </div>
            <div>
              <dt className="font-medium">Output directory</dt>
              <dd className="break-all font-mono">{data.settings.directory}</dd>
            </div>
            <div>
              <dt className="font-medium">Maximum file size</dt>
              <dd>{data.settings.maxFileSizeMiB} MiB</dd>
            </div>
            <div>
              <dt className="font-medium">Retention</dt>
              <dd>{data.settings.retentionDays} days</dd>
            </div>
            <div>
              <dt className="font-medium">Maximum archive size</dt>
              <dd>{data.settings.maxArchiveSizeMiB} MiB</dd>
            </div>
          </dl>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Files rotate at midnight UTC or when they reach the size threshold.
            Archive limits exclude the active file; cleanup is asynchronous.
            Mount persistent storage when running in Docker or Kubernetes and
            use one writer per directory.
          </p>
          <p className="text-sm text-slate-600 dark:text-slate-400">
            Returned data, passwords and tokens are excluded at every level.
            File output is best effort: failed writes are counted and are not
            replayed. Proxy queries include session details; their result counts
            and outcome are unknown.
          </p>
        </>
      )}
    </div>
  );
}
