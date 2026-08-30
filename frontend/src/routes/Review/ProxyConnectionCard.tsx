import { useEffect, useState } from "react";
import {
  CheckIcon,
  ClipboardIcon,
  ClockIcon,
  EyeIcon,
  EyeSlashIcon,
} from "@heroicons/react/24/outline";
import { ProxyResponse } from "../../api/ExecutionRequestApi";
import { DatabaseType } from "../../api/DatasourceApi";
import Button from "../../components/Button";
import useNotification from "../../hooks/useNotification";

const MASKED_PASSWORD = "••••••••";

function formatRemaining(expiresAt: Date, now: Date): string {
  const totalMinutes = Math.floor(
    (expiresAt.getTime() - now.getTime()) / 60000,
  );
  if (totalMinutes <= 0) {
    return "Expired";
  }
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0
    ? `Expires in ${hours} h ${minutes} min`
    : `Expires in ${minutes} min`;
}

function ProxyConnectionCard({ proxy }: { proxy: ProxyResponse }) {
  const { addNotification } = useNotification();
  const [revealed, setRevealed] = useState(false);
  const [copied, setCopied] = useState(false);
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, []);

  // The backend knows no host only before any request has reached it, which can't happen
  // here — but the page the user is looking at is always a working fallback.
  const host = proxy.host ?? window.location.hostname;
  const scheme = proxy.type === DatabaseType.POSTGRES ? "postgresql" : "mysql";
  const path = proxy.databaseName ? `/${proxy.databaseName}` : "";
  const uri = (password: string) =>
    `${scheme}://${proxy.username}:${password}@${host}:${proxy.port}${path}`;

  const copyUri = () => {
    navigator.clipboard.writeText(uri(proxy.password)).then(
      () => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      },
      () => {
        addNotification({
          title: "Failed to copy to clipboard",
          text: "You can copy the connection details manually instead.",
          type: "error",
        });
      },
    );
  };

  return (
    <div className="my-4 flex flex-col gap-4 rounded-md border border-gray-300 bg-white p-4 dark:border-slate-700 dark:bg-slate-900">
      <div className="flex flex-row items-center justify-between">
        <div className="flex flex-row items-center gap-2.5">
          <span className="h-2 w-2 rounded-full bg-green-500 ring-2 ring-green-500/20"></span>
          <span className="text-sm font-medium text-slate-900 dark:text-slate-50">
            Proxy session active
          </span>
        </div>
        {proxy.expiresAt && (
          <div
            className="flex flex-row items-center gap-1.5 text-xs text-slate-500 dark:text-slate-400"
            title={proxy.expiresAt.toLocaleString()}
          >
            <ClockIcon className="h-3.5 w-3.5" />
            <span>{formatRemaining(proxy.expiresAt, now)}</span>
          </div>
        )}
      </div>
      <div className="flex flex-row items-center gap-2">
        <div className="min-w-0 flex-1 truncate rounded border border-slate-200 bg-slate-50 px-3 py-2 font-mono text-sm text-slate-900 dark:border-slate-800 dark:bg-slate-950 dark:text-slate-200">
          <span className="text-slate-400 dark:text-slate-500">
            {scheme}://
          </span>
          <span className="text-indigo-700 dark:text-indigo-300">
            {proxy.username}
          </span>
          <span className="text-slate-400 dark:text-slate-500">:</span>
          {revealed ? (
            <span className="text-indigo-700 dark:text-indigo-300">
              {proxy.password}
            </span>
          ) : (
            <span className="text-slate-400 dark:text-slate-500">
              {MASKED_PASSWORD}
            </span>
          )}
          <span className="text-slate-400 dark:text-slate-500">@</span>
          {host}
          <span className="text-slate-400 dark:text-slate-500">:</span>
          {proxy.port}
          {path && (
            <>
              <span className="text-slate-400 dark:text-slate-500">/</span>
              {proxy.databaseName}
            </>
          )}
        </div>
        <Button onClick={copyUri} dataTestId="copy-proxy-uri">
          <span className="flex flex-row items-center gap-1.5">
            {copied ? (
              <CheckIcon className="h-4 w-4" />
            ) : (
              <ClipboardIcon className="h-4 w-4" />
            )}
            {copied ? "Copied" : "Copy"}
          </span>
        </Button>
      </div>
      <div className="grid grid-cols-3 gap-4">
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Host
          </span>
          <span className="truncate font-mono text-sm text-slate-900 dark:text-slate-50">
            {host}
          </span>
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Port
          </span>
          <span className="font-mono text-sm text-slate-900 dark:text-slate-50">
            {proxy.port}
          </span>
        </div>
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Database
          </span>
          <span className="truncate font-mono text-sm text-slate-900 dark:text-slate-50">
            {proxy.databaseName || "—"}
          </span>
        </div>
        <div className="flex min-w-0 flex-col gap-0.5">
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Username
          </span>
          <span className="truncate font-mono text-sm text-slate-900 dark:text-slate-50">
            {proxy.username}
          </span>
        </div>
        <div className="flex flex-col gap-0.5">
          <span className="text-xs text-slate-500 dark:text-slate-400">
            Password
          </span>
          <span className="flex flex-row items-center gap-2">
            <span className="font-mono text-sm text-slate-900 dark:text-slate-50">
              {revealed ? proxy.password : MASKED_PASSWORD}
            </span>
            <button
              type="button"
              onClick={() => setRevealed(!revealed)}
              title={revealed ? "Hide password" : "Show password"}
              className="text-slate-400 hover:text-slate-600 dark:text-slate-500 dark:hover:text-slate-300"
            >
              {revealed ? (
                <EyeSlashIcon className="h-4 w-4" />
              ) : (
                <EyeIcon className="h-4 w-4" />
              )}
            </button>
          </span>
        </div>
      </div>
      <div className="border-t border-slate-200 pt-2.5 text-xs text-slate-500 dark:border-slate-800">
        Connect with the client of your choice. Every statement is recorded in
        the audit log.
      </div>
    </div>
  );
}

export default ProxyConnectionCard;
