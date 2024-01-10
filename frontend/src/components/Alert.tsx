import { XCircleIcon } from "@heroicons/react/20/solid";
import { ReactNode } from "react";

const Error = ({
  heading,
  children,
}: {
  heading: string;
  children: ReactNode;
}) => (
  <div className="rounded-md bg-red-50 p-4 dark:text-red-400 dark:bg-red-400/10">
    <div className="flex">
      <div className="flex-shrink-0">
        <XCircleIcon
          className="h-5 w-5 text-red-400 dark:text-red-300 dark:bg-red-400/10"
          aria-hidden="true"
        />
      </div>
      <div className="ml-3">
        <h3 className="text-sm font-medium text-red-800 dark:text-red-300">
          {heading}
        </h3>
        <div className="mt-2 text-sm text-red-700 dark:text-red-400">
          <ul role="list" className="list-disc space-y-1 pl-5">
            {children}
          </ul>
        </div>
      </div>
    </div>
  </div>
);
export default Error;

import { ExclamationTriangleIcon } from "@heroicons/react/20/solid";

export function Warning({ children }: { children: string | ReactNode }) {
  return (
    <div className="border-l-4 border-yellow-400 bg-yellow-50 p-4 dark:bg-yellow-400/10 dark:border-yellow-500">
      <div className="flex">
        <div className="flex-shrink-0">
          <ExclamationTriangleIcon
            className="h-5 w-5 text-yellow-400 dark:text-yellow-500"
            aria-hidden="true"
          />
        </div>
        <div className="ml-3 text-sm text-yellow-700 dark:text-yellow-400">
          <p>{children}</p>
        </div>
      </div>
    </div>
  );
}
