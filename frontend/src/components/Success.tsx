import { CheckCircleIcon } from "@heroicons/react/20/solid";

const Success = ({ heading, text }: { heading: string; text: string }) => (
  <div className="rounded-md bg-green-50 dark:text-green-400 dark:bg-green-400/10 p-4">
    <div className="flex">
      <div className="flex-shrink-0">
        <CheckCircleIcon
          className="h-5 w-5 text-green-400 dark:text-green-300"
          aria-hidden="true"
        />
      </div>
      <div className="ml-3">
        <h3 className="text-sm font-medium text-green-800 dark:text-green-400">
          {heading}
        </h3>
        <div className="mt-2 text-sm text-green-700">
          <p>{text}</p>
        </div>
      </div>
    </div>
  </div>
);
export default Success;
