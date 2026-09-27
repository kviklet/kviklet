import { useContext, useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import Button from "../../components/Button";
import Spinner from "../../components/Spinner";
import { Error } from "../../components/Alert";
import { EnterpriseFeaturePage } from "../../components/EnterpriseFeature";
import { UserStatusContext } from "../../components/UserStatusProvider";
import {
  McpConsentDetailsResult,
  getMcpConsentDetails,
  submitMcpConsent,
} from "../../api/McpOAuthApi";

const hostOf = (url: string): string => {
  try {
    return new URL(url).host;
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
          Allow <span data-testid="mcp-client-name">{details.clientName}</span>{" "}
          to use Kviklet as you?
        </h1>
        <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">
          The application chose this name itself; Kviklet has not verified it.
        </p>
        <div className="mt-4 space-y-3 text-sm text-slate-700 dark:text-slate-300">
          <p>
            If you allow access, this MCP client can do everything you can do in
            Kviklet
            {userStatus ? (
              <>
                {" "}
                as <span className="font-medium">{userStatus.email}</span>
              </>
            ) : null}
            , with your permissions, until its login expires or your account is
            deactivated.
          </p>
          <p>
            You will be sent back to{" "}
            <span className="font-mono font-medium">
              {hostOf(details.redirectUri)}
            </span>
            . Only allow access if you just started this login from an MCP
            client like Claude Code yourself.
          </p>
        </div>
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
            Deny
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
