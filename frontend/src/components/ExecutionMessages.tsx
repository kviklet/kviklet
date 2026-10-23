import { FC, useState } from "react";
import { ExecutionMessage } from "../api/ExecutionRequestApi";

const COLLAPSED_LINES = 5;

const levelStyles: Record<ExecutionMessage["level"], string> = {
  NOTICE: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300",
  WARNING: "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300",
};

// The notices and warnings the database sent during a run. They belong to the
// whole run rather than one statement. A loop raising a notice per row can send
// thousands, so only the first lines show until asked; `total` is what the
// database sent when fewer than that were kept (the activity log stores 100).
const ExecutionMessages: FC<{
  messages: ExecutionMessage[];
  total?: number;
}> = ({ messages, total = messages.length }) => {
  const [showAll, setShowAll] = useState(false);
  const visible = showAll ? messages : messages.slice(0, COLLAPSED_LINES);
  const notStored = total - messages.length;

  return (
    <div className="flex flex-col gap-1" data-testid="execution-messages">
      <div className="text-[11px] font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
        Messages · {total.toLocaleString()}
      </div>
      <ul className="flex flex-col gap-1">
        {visible.map((message, i) => (
          <li key={i} className="flex items-baseline gap-2.5">
            <span
              className={`w-16 flex-none rounded-sm py-px text-center text-[10px] font-semibold tracking-wide ${
                levelStyles[message.level]
              }`}
            >
              {message.level}
            </span>
            <span className="min-w-0 whitespace-pre-wrap break-words font-mono text-xs text-slate-700 dark:text-slate-300">
              {message.message}
            </span>
          </li>
        ))}
      </ul>
      {visible.length < messages.length && (
        <button
          type="button"
          onClick={() => setShowAll(true)}
          className="self-start py-1 text-xs font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300"
        >
          Show all {messages.length.toLocaleString()} messages
        </button>
      )}
      {visible.length === messages.length && notStored > 0 && (
        <span className="text-xs text-slate-500 dark:text-slate-400">
          {notStored.toLocaleString()} more not stored
        </span>
      )}
    </div>
  );
};

export default ExecutionMessages;
