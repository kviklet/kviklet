import { useState } from "react";
import Button from "./Button";
import { OncallGrantKind } from "../api/UserApi";

const DURATION_PRESETS = [
  { label: "1h", minutes: 60 },
  { label: "2h", minutes: 120 },
  { label: "4h", minutes: 240 },
  { label: "8h", minutes: 480 },
  { label: "1d", minutes: 1440 },
  { label: "3d", minutes: 4320 },
  { label: "7d", minutes: 10080 },
  { label: "10d", minutes: 14400 },
];

export type OncallGrantSubmit = (
  userId: string,
  kind: OncallGrantKind,
  durationMinutes: number,
  reason: string,
  bypassApproval: boolean,
) => Promise<boolean>;

function OncallGrantModal(props: {
  userId: string;
  userLabel?: string;
  mode: "start" | "request";
  disableModal: () => void;
  onSubmit: OncallGrantSubmit;
}) {
  const [kind, setKind] = useState<OncallGrantKind>("ONCALL");
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [reason, setReason] = useState("");
  const [bypassApproval, setBypassApproval] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const selectKind = (next: OncallGrantKind) => {
    setKind(next);
    setBypassApproval(next === "OUTAGE");
  };

  const onSubmit = async () => {
    setSubmitting(true);
    const ok = await props.onSubmit(
      props.userId,
      kind,
      durationMinutes,
      reason,
      bypassApproval,
    );
    setSubmitting(false);
    if (ok) {
      props.disableModal();
    }
  };

  const isRequest = props.mode === "request";

  return (
    <div className="w-2xl rounded bg-slate-50 p-3 shadow dark:bg-slate-900">
      <h2 className="mb-1 text-lg font-semibold">
        {isRequest ? "Request on-call / outage" : "Start on-call / outage"}
      </h2>
      <p className="mb-4 text-sm text-slate-600 dark:text-slate-400">
        {isRequest
          ? "This request is only for you. A manager must approve it before access starts; they will be notified in Slack."
          : `Grants ${
              props.userLabel || "this user"
            } access to every connection until the period ends.`}
      </p>
      <div className="mb-3">
        <div className="mb-1 text-sm font-medium text-slate-700 dark:text-slate-200">
          Kind
        </div>
        <div className="flex gap-2">
          <Button
            htmlType="button"
            variant={kind === "ONCALL" ? "primary" : undefined}
            onClick={() => selectKind("ONCALL")}
            dataTestId="oncall-kind-oncall"
          >
            On-call
          </Button>
          <Button
            htmlType="button"
            variant={kind === "OUTAGE" ? "danger" : undefined}
            onClick={() => selectKind("OUTAGE")}
            dataTestId="oncall-kind-outage"
          >
            Outage
          </Button>
        </div>
      </div>
      <div className="mb-3">
        <div className="mb-1 text-sm font-medium text-slate-700 dark:text-slate-200">
          Duration
        </div>
        <div className="flex flex-wrap gap-2">
          {DURATION_PRESETS.map((preset) => (
            <Button
              key={preset.minutes}
              htmlType="button"
              size="sm"
              variant={
                durationMinutes === preset.minutes ? "primary" : undefined
              }
              onClick={() => setDurationMinutes(preset.minutes)}
              dataTestId={`oncall-duration-${preset.label}`}
            >
              {preset.label}
            </Button>
          ))}
        </div>
      </div>
      <div className="mb-3">
        <label
          className="mb-1 block text-sm font-medium text-slate-700 dark:text-slate-200"
          htmlFor="oncall-reason"
        >
          Reason (optional)
        </label>
        <textarea
          id="oncall-reason"
          className="block w-full rounded-md border border-slate-300 px-3 py-2 text-sm dark:border-slate-700 dark:bg-slate-900"
          rows={2}
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          data-testid="oncall-reason"
        />
      </div>
      {!isRequest && (
        <div className="mb-4 flex items-center">
          <input
            id="oncall-bypass"
            className="h-4 w-4 rounded border-slate-300 text-indigo-600 focus:ring-indigo-600"
            type="checkbox"
            checked={bypassApproval}
            onChange={(event) => setBypassApproval(event.target.checked)}
            data-testid="oncall-bypass-approval"
          />
          <label
            htmlFor="oncall-bypass"
            className="ml-2 text-sm text-slate-700 dark:text-slate-200"
          >
            Bypass approval (default on for outage, off for on-call)
          </label>
        </div>
      )}
      <div className="flex flex-row justify-end space-x-2">
        <Button onClick={props.disableModal} htmlType="button">
          Cancel
        </Button>
        <Button
          htmlType="button"
          variant="primary"
          onClick={() => void onSubmit()}
          dataTestId="start-oncall-confirm"
        >
          {submitting
            ? isRequest
              ? "Submitting..."
              : "Starting..."
            : isRequest
            ? "Request"
            : "Start"}
        </Button>
      </div>
    </div>
  );
}

export default OncallGrantModal;
