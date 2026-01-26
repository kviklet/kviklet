import { ExclamationTriangleIcon } from "@heroicons/react/20/solid";

export default function ShellResult({
  messages,
  errors,
  finished,
  exitCode,
}: {
  messages: string[];
  errors: string[];
  finished: boolean;
  exitCode?: number | null;
}) {
  return (
    <div className="flex flex-col justify-center">
      {!finished && (
        <div className="mb-2 border-l-4 border-yellow-400 bg-yellow-50 p-4 dark:border-yellow-500 dark:bg-yellow-400/10">
          <div className="flex">
            <div className="flex-shrink-0">
              <ExclamationTriangleIcon
                className="h-5 w-5 text-yellow-400 dark:text-yellow-500"
                aria-hidden="true"
              />
            </div>
            <div className="ml-3 text-sm text-yellow-700 dark:text-yellow-400">
              <p>
                Command is still running on server and will beterminated after 5 minutes. Output below is partial.
              </p>
            </div>
          </div>
        </div>
      )}
      {finished && exitCode !== null && exitCode !== undefined && (
        <div className="my-1 text-slate-700 dark:text-slate-400">
          <span
            className={
              exitCode === 0
                ? "text-green-600 dark:text-green-400"
                : "text-red-600 dark:text-red-400"
            }
          >
            Exit code: {exitCode}
          </span>
        </div>
      )}
      <div className="my-1 font-mono text-sm text-slate-700 dark:text-slate-400">
        {messages.map((message, index) => (
          <div key={index}>{message}</div>
        ))}
      </div>
      {errors.length > 0 && (
        <div className="font-mono text-sm text-red-500">
          {errors.map((error, index) => (
            <div key={index}>{error}</div>
          ))}
        </div>
      )}
    </div>
  );
}
