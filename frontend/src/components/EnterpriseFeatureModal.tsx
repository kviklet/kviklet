import { ReactNode } from "react";
import { SparklesIcon } from "@heroicons/react/24/outline";
import { Link } from "react-router-dom";
import Modal from "./Modal";
import Button from "./Button";

/**
 * Upsell dialog shown when a user clicks an enterprise-only action without a
 * valid license: pitches the feature instead of just refusing it.
 */
const EnterpriseFeatureModal = (props: {
  /** Feature name shown as the heading, e.g. "Database Proxy". */
  feature: string;
  /** The pitch: what the feature does and why it's worth upgrading for. */
  children: ReactNode;
  setVisible: (visible: boolean) => void;
}) => {
  return (
    <Modal setVisible={props.setVisible}>
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-lg dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center gap-2">
          <SparklesIcon className="h-6 w-6 text-purple-600 dark:text-purple-400" />
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-50">
            {props.feature}
          </h2>
          <span
            className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium
                       text-purple-800 dark:bg-purple-900 dark:text-purple-200"
          >
            Enterprise
          </span>
        </div>
        <div className="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-300">
          {props.children}
        </div>
        <div className="mt-6 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <Link
            to="/settings/license"
            className="text-sm text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
          >
            Already have a license? Upload it here.
          </Link>
          <div className="flex items-center gap-2">
            <Button onClick={() => props.setVisible(false)}>Close</Button>
            <a
              href="https://kviklet.dev"
              target="_blank"
              rel="noopener noreferrer"
            >
              <Button variant="primary">Get a license at kviklet.dev</Button>
            </a>
          </div>
        </div>
      </div>
    </Modal>
  );
};

export default EnterpriseFeatureModal;
