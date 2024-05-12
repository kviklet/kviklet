import { CheckCircleIcon, XCircleIcon } from "@heroicons/react/20/solid";
import { ReactNode } from "react";

import { ExclamationTriangleIcon } from "@heroicons/react/20/solid";

export function Warning({ children }: { children: string | ReactNode }) {
  return (
    <div className="border-l-4 border-yellow-400 bg-yellow-50 p-4 dark:border-yellow-500 dark:bg-yellow-400/10">
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

export function Error({ children }: { children: string | ReactNode }) {
  return (
    <div className="border-l-4 border-red-400 bg-red-50 p-4 dark:border-red-500 dark:bg-red-400/10">
      <div className="flex">
        <div className="flex-shrink-0">
          <XCircleIcon
            className="h-5 w-5 text-red-400 dark:text-red-500"
            aria-hidden="true"
          />
        </div>
        <div className="ml-3 text-sm text-red-700 dark:text-red-400">
          <p>{children}</p>
        </div>
      </div>
    </div>
  );
}

export function Success({ children }: { children: string | ReactNode }) {
  return (
    <div className="border-l-4 border-green-400 bg-green-50 p-4 dark:border-green-500 dark:bg-green-400/10">
      <div className="flex">
        <div className="flex-shrink-0">
          <CheckCircleIcon
            className="h-5 w-5 text-green-400 dark:text-green-300"
            aria-hidden="true"
          />
        </div>
        <div className="ml-3 text-sm text-green-700 dark:text-green-400">
          <p>{children}</p>
        </div>
      </div>
    </div>
  );
}
