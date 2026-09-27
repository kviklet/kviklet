import { useContext, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import Button from "../../components/Button";
import Spinner from "../../components/Spinner";
import { Error, WarningBanner } from "../../components/Alert";
import { EnterpriseFeaturePage } from "../../components/EnterpriseFeature";
import { UserStatusContext } from "../../components/UserStatusProvider";
import {
  McpConsentDetailsResult,
  getMcpConsentDetails,
  submitMcpConsent,
} from "../../api/McpOAuthApi";

// Custom-scheme redirects of native apps (com.example.app:/callback) have no host; show them whole.
const hostOf = (url: string): string => {
  try {
    return new URL(url).host || url;
  } catch {
    return url;
  }
};

/**
 * Asks the user whether an MCP client may act on their behalf. Shown for every authorization:
 * anyone can register a client under any name, so the user has to confirm each time.
 */
const McpConsent = () => {
  const [searchParams] = useSearchParams();
  const state = searchParams.get("state") ?? "";
  const { userStatus } = useContext(UserStatusContext);
  const [result, setResult] = useState<McpConsentDetailsResult>();
  const [submitting, setSubmitting] = useState(false);
  const [submitFailed, setSubmitFailed] = useState(false);

  useEffect(() => {
    void getMcpConsentDetails(state).then(setResult);
  }, [state]);

  if (!result) {
    return <Spinner size="lg" page />;
  }
  if (result.status === "licenseRequired") {
    return <EnterpriseFeaturePage feature="mcpServer" />;
  }
  if (result.status === "error") {
    return (
      <div className="mx-auto mt-10 max-w-md">
        <Error>
          This authorization request is no longer valid. Start the login from
          your MCP client again.
        </Error>
      </div>
    );
  }

  const { details } = result;
  const decide = async (approve: boolean) => {
    setSubmitting(true);
    const redirectUri = await submitMcpConsent(
      details.clientId,
      state,
      approve,
    );
    if (redirectUri) {
      window.location.assign(redirectUri);
    } else {
      setSubmitting(false);
      setSubmitFailed(true);
    }
  };

  return (
    <div className="mx-auto mt-10 max-w-md">
      <div className="rounded-md bg-white p-6 shadow-xl dark:bg-slate-900 dark:shadow-none">
        <h1 className="text-xl font-semibold text-slate-900 dark:text-slate-50">
          Connect an MCP client to Kviklet
        </h1>
        <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-slate-500 dark:text-slate-400">Client</dt>
          <dd
            className="font-medium text-slate-900 dark:text-slate-50"
            data-testid="mcp-client-name"
          >
            {details.clientName}
          </dd>
          <dt className="text-slate-500 dark:text-slate-400">Returns to</dt>
          <dd className="font-mono text-slate-900 dark:text-slate-50">
            {hostOf(details.redirectUri)}
          </dd>
        </dl>
        <p className="mt-4 text-sm text-slate-700 dark:text-slate-300">
          It will act as{" "}
          {userStatus ? (
            <span className="font-medium">{userStatus.email}</span>
          ) : (
            "you"
          )}
          : it can see and do everything you can in Kviklet, and nothing more.
        </p>
        <WarningBanner className="mt-4">
          Only continue if you just started this from your MCP client yourself.
        </WarningBanner>
        {submitFailed && (
          <div className="mt-4">
            <Error>
              This authorization request is no longer valid. Start the login
              from your MCP client again.
            </Error>
          </div>
        )}
        <div className="mt-6 flex justify-end gap-2">
          <Button
            onClick={() => void decide(false)}
            variant={submitting ? "disabled" : undefined}
            dataTestId="mcp-consent-deny"
          >
            Cancel
          </Button>
          <Button
            onClick={() => void decide(true)}
            variant={submitting ? "disabled" : "primary"}
            dataTestId="mcp-consent-allow"
          >
            Allow access
          </Button>
        </div>
      </div>
    </div>
  );
};

export default McpConsent;
