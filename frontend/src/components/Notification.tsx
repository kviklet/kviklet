import { useState } from "react";
import { Transition } from "@headlessui/react";
import { CheckCircleIcon } from "@heroicons/react/24/outline";
import { XMarkIcon } from "@heroicons/react/20/solid";

export default function Notification(props: { title: string; text: string }) {
  const [show, setShow] = useState(true);

  return (
    <Transition
      show={show}
      enter="transform ease-out duration-300 transition"
      enterFrom="translate-y-2 opacity-0 sm:translate-y-0 sm:translate-x-2"
      enterTo="translate-y-0 opacity-100 sm:translate-x-0"
      leave="transition ease-in duration-100"
      leaveFrom="opacity-100"
      leaveTo="opacity-0"
    >
      <div
        className="pointer-events-auto ml-auto w-full max-w-sm overflow-hidden rounded-lg bg-white shadow-lg ring-1 ring-black ring-opacity-5
      dark:border dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 dark:shadow-none"
      >
        <div className="p-4">
          <div className="flex items-start">
            <div className="flex-shrink-0">
              <CheckCircleIcon
                className="h-6 w-6 text-green-400"
                aria-hidden="true"
              />
            </div>
            <div className="ml-3 w-0 flex-1 pt-0.5">
              <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                {props.title}
              </p>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                {props.text}
              </p>
            </div>
            <div className="ml-4 flex flex-shrink-0">
              <button
                type="button"
                className="inline-flex rounded-md text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 hover:text-slate-500"
                onClick={() => {
                  setShow(false);
                }}
              >
                <span className="sr-only">Close</span>
                <XMarkIcon className="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  );
}

function ErrorNotification(props: { title: string; text: string }) {
  const [show, setShow] = useState(true);

  return (
    <Transition
      show={show}
      enter="transform ease-out duration-300 transition"
      enterFrom="translate-y-2 opacity-0 sm:translate-y-0 sm:translate-x-2"
      enterTo="translate-y-0 opacity-100 sm:translate-x-0"
      leave="transition ease-in duration-100"
      leaveFrom="opacity-100"
      leaveTo="opacity-0"
    >
      <div
        className="pointer-events-auto ml-auto w-full max-w-sm overflow-hidden rounded-lg bg-white shadow-lg ring-1 ring-black ring-opacity-5
      dark:border dark:border-slate-700 dark:bg-slate-900 dark:text-slate-50 dark:shadow-none"
      >
        <div className="p-4">
          <div className="flex items-start">
            <div className="flex-shrink-0">
              <XMarkIcon className="h-6 w-6 text-red-400" aria-hidden="true" />
            </div>
            <div className="ml-3 w-0 flex-1 pt-0.5">
              <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                {props.title}
              </p>
              <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">
                {props.text}
              </p>
            </div>
            <div className="ml-4 flex flex-shrink-0">
              <button
                type="button"
                className="inline-flex rounded-md text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 hover:text-slate-500"
                onClick={() => {
                  setShow(false);
                }}
              >
                <span className="sr-only">Close</span>
                <XMarkIcon className="h-5 w-5" aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  );
}

export { ErrorNotification };
