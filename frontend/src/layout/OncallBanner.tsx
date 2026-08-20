import { useContext } from "react";
import { UserStatusContext } from "../components/UserStatusProvider";
import { OncallGrantKind } from "../api/UserApi";

function parseUtc(value: string): Date {
  const utcString =
    value.includes("Z") || value.includes("+")
      ? value
      : value.replace(" ", "T") + "Z";
  return new Date(utcString);
}

function formatRemaining(endsAt: Date): string {
  const minutes = Math.max(
    0,
    Math.ceil((endsAt.getTime() - Date.now()) / 60000),
  );
  if (minutes < 60) {
    return `${minutes}m remaining`;
  }
  const hours = Math.floor(minutes / 60);
  const rem = minutes % 60;
  if (hours < 24) {
    return rem === 0 ? `${hours}h remaining` : `${hours}h ${rem}m remaining`;
  }
  const days = Math.floor(hours / 24);
  return `${days}d remaining`;
}

function kindLabel(kind: OncallGrantKind): string {
  return kind === "OUTAGE" ? "Outage" : "On-call";
}

function OncallBanner() {
  const { userStatus } = useContext(UserStatusContext);
  const grant =
    userStatus && userStatus !== false
      ? userStatus.activeOncallGrant
      : undefined;
  if (!grant) {
    return null;
  }

  const endsAt = parseUtc(grant.endsAt);
  const isOutage = grant.kind === "OUTAGE";

  return (
    <div
      className={`border-b px-4 py-2 text-sm ${
        isOutage
          ? "border-red-200 bg-red-50 text-red-900 dark:border-red-900 dark:bg-red-950 dark:text-red-100"
          : "border-amber-200 bg-amber-50 text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100"
      }`}
      data-testid="oncall-banner"
    >
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-center gap-x-2">
        <span className="font-semibold">{kindLabel(grant.kind)} access</span>
        <span>
          to every connection is active ({formatRemaining(endsAt)}
          {grant.bypassApproval ? ", approval bypassed" : ""}).
        </span>
      </div>
    </div>
  );
}

export default OncallBanner;
