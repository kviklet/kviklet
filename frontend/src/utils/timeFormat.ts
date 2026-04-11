function pad(n: number): string {
  return n.toString().padStart(2, "0");
}

function formatAbsoluteTime(date: Date): string {
  const tz = -date.getTimezoneOffset();
  const sign = tz >= 0 ? "+" : "-";
  const absH = pad(Math.floor(Math.abs(tz) / 60));
  const absM = pad(Math.abs(tz) % 60);
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}` +
    `${sign}${absH}:${absM}`
  );
}

function timeSince(date: Date): string {
  const seconds = Math.floor((new Date().getTime() - date.getTime()) / 1000);

  const units: [number, string][] = [
    [31536000, "year"],
    [2592000, "month"],
    [86400, "day"],
    [3600, "hour"],
    [60, "minute"],
  ];

  for (const [divisor, unit] of units) {
    const value = Math.floor(seconds / divisor);
    if (value > 0) {
      return `${value} ${unit}${value !== 1 ? "s" : ""} ago`;
    }
  }
  return Math.floor(seconds) + " seconds ago";
}

export { formatAbsoluteTime, timeSince };
