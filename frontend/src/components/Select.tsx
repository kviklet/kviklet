export default function Select({
  options,
  label,
}: {
  options: string[];
  label: string;
}) {
  return (
    <div>
      <label
        htmlFor="location"
        className="block text-sm font-medium leading-6 text-slate-900 dark:text-slate-50"
      >
        {label}
      </label>
      <select
        id="location"
        name="location"
        className="mt-2 block w-full dark:bg-slate-900 rounded-md border-0 py-1.5 pl-3 pr-10 text-slate-900 ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6 dark:text-slate-50"
        defaultValue="Canada"
      >
        {options.map((option) => (
          <option>{option}</option>
        ))}
      </select>
    </div>
  );
}
