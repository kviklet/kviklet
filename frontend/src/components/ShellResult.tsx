export default function ShellResult({
  messages,
  errors,
}: {
  messages: string[];
  errors: string[];
}) {
  return (
    <div className="flex flex-col justify-center">
      <div className="flex justify-start space-x-2">
        <div className="my-1 text-slate-700 dark:text-slate-400">
          {messages.length + errors.length + " messages"}
        </div>
      </div>
      <div className="my-1 text-slate-700 dark:text-slate-400">
        {messages.map((message, index) => (
          <div key={index}>{message}</div>
        ))}
      </div>
      <div className="text-red-500">
        {errors.map((error, index) => (
          <div key={index}>{error}</div>
        ))}
      </div>
    </div>
  );
}
