import type { TimezoneMode } from "../components/TimezoneProvider";

function pad(n: number): string {
  return n.toString().padStart(2, "0");
}

function formatAbsoluteTime(
  date: Date,
  timezone: TimezoneMode = "local",
): string {
  if (timezone === "utc") {
    return (
      `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(
        date.getUTCDate(),
      )}` +
      `T${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}:${pad(
        date.getUTCSeconds(),
      )}Z`
    );
  }
  const tz = -date.getTimezoneOffset();
  const sign = tz >= 0 ? "+" : "-";
  const absH = pad(Math.floor(Math.abs(tz) / 60));
  const absM = pad(Math.abs(tz) % 60);
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(
      date.getSeconds(),
    )}` +
    `${sign}${absH}:${absM}`
  );
}

/** Get the local timezone offset label, e.g. "+09:00" */
function localTimezoneLabel(): string {
  const tz = -new Date().getTimezoneOffset();
  const sign = tz >= 0 ? "+" : "-";
  const h = pad(Math.floor(Math.abs(tz) / 60));
  const m = pad(Math.abs(tz) % 60);
  return `${sign}${h}:${m}`;
}

export { formatAbsoluteTime, localTimezoneLabel };
