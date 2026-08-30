import { ReactNode } from "react";
import { SparklesIcon } from "@heroicons/react/24/outline";
import { Link } from "react-router-dom";
import Modal from "./Modal";
import Button from "./Button";

// Every enterprise upsell surface (modal or page) pulls its copy from here so the
// pitch for a feature reads the same wherever the user runs into the license gate.
const enterpriseFeatures = {
  databaseProxy: {
    title: "Database Proxy",
    pitch: (
      <>
        <p>
          Connect psql, mysql, DataGrip, or any other native client straight to
          an approved temporary-access request — no credentials to hand out, no
          separate tunnel.
        </p>
        <p>
          Every statement passes through Kviklet and lands in the audit log, so
          temporary access stays fully reviewable even outside the web editor.
        </p>
      </>
    ),
  },
  auditLog: {
    title: "Audit Log Export & Filtering",
    pitch: (
      <>
        <p>
          Filter the audit log by date range and export it as a single file —
          every executed statement with who ran it, when, and on which
          connection.
        </p>
        <p>
          Hand auditors a full record of database access without giving them a
          Kviklet login, and keep long-term archives for compliance.
        </p>
      </>
    ),
  },
  apiKeys: {
    title: "API Keys",
    pitch: (
      <>
        <p>
          Authenticate scripts and integrations against the Kviklet API with
          dedicated keys instead of user credentials.
        </p>
        <p>
          Keys expire after a configurable lifetime and can be deleted at any
          time, so automated access stays controlled and revocable.
        </p>
      </>
    ),
  },
  roleSync: {
    title: "Role Sync",
    pitch: (
      <>
        <p>
          Map groups from your identity provider to Kviklet roles once, and
          every user&apos;s permissions are assigned automatically at login.
        </p>
        <p>
          On- and offboarding happens in your IdP alone — no manual role
          assignments to keep in sync.
        </p>
      </>
    ),
  },
  roleRequirements: {
    title: "Role-Specific Requirements",
    pitch: (
      <>
        <p>
          Require approvals from specific roles — for example one review from a
          DBA role — on top of the total number of reviews a connection needs.
        </p>
        <p>
          That way the right people sign off on risky changes, not just any
          available reviewer.
        </p>
      </>
    ),
  },
} satisfies Record<string, { title: string; pitch: ReactNode }>;

type EnterpriseFeatureKey = keyof typeof enterpriseFeatures;

const EnterpriseBadge = () => (
  <span
    className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-medium
               text-purple-800 dark:bg-purple-900 dark:text-purple-200"
  >
    Enterprise
  </span>
);

const LicenseLinks = () => (
  <>
    <Link
      to="/settings/license"
      className="text-sm text-blue-600 underline hover:text-blue-800 dark:text-blue-400 dark:hover:text-blue-300"
    >
      Already have a license? Upload it here.
    </Link>
    <a href="https://kviklet.dev" target="_blank" rel="noopener noreferrer">
      <Button variant="primary">Get a license at kviklet.dev</Button>
    </a>
  </>
);

/**
 * Upsell dialog shown when a user clicks an enterprise-only action without a
 * valid license: pitches the feature instead of just refusing it.
 */
const EnterpriseFeatureModal = (props: {
  feature: EnterpriseFeatureKey;
  setVisible: (visible: boolean) => void;
}) => {
  const { title, pitch } = enterpriseFeatures[props.feature];
  return (
    <Modal setVisible={props.setVisible}>
      <div className="rounded-lg border border-slate-200 bg-white p-6 shadow-lg dark:border-slate-700 dark:bg-slate-900">
        <div className="flex items-center gap-2">
          <SparklesIcon className="h-6 w-6 text-purple-600 dark:text-purple-400" />
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-50">
            {title}
          </h2>
          <EnterpriseBadge />
        </div>
        <div className="mt-3 space-y-2 text-sm text-slate-600 dark:text-slate-300">
          {pitch}
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

/**
 * Full-page upsell for enterprise-only settings pages, shown in place of the
 * feature when no valid license is present.
 */
const EnterpriseFeaturePage = (props: { feature: EnterpriseFeatureKey }) => {
  const { title, pitch } = enterpriseFeatures[props.feature];
  return (
    <div className="flex h-64 flex-col items-center justify-center text-center">
      <SparklesIcon className="mb-4 h-12 w-12 text-purple-600 dark:text-purple-400" />
      <div className="mb-2 flex items-center gap-2">
        <h2 className="text-xl font-semibold text-slate-700 dark:text-slate-300">
          {title}
        </h2>
        <EnterpriseBadge />
      </div>
      <div className="mb-6 max-w-xl space-y-2 text-sm text-slate-500 dark:text-slate-400">
        {pitch}
      </div>
      <div className="flex flex-col items-center gap-3">
        <LicenseLinks />
      </div>
    </div>
  );
};

export { EnterpriseFeatureModal, EnterpriseFeaturePage };
export type { EnterpriseFeatureKey };
