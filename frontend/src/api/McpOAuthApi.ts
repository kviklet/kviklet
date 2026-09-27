import { z } from "zod";
import baseUrl, { apiFetch } from "./base";

// The OAuth flow MCP clients (e.g. Claude Code) use to log in to Kviklet. The backend is the
// authorization server; the frontend only logs the user in and asks for their consent.

const mcpConsentDetailsSchema = z.object({
  clientId: z.string(),
  clientName: z.string(),
  redirectUri: z.string(),
  scopes: z.array(z.string()),
});

export type McpConsentDetails = z.infer<typeof mcpConsentDetailsSchema>;

export type McpConsentDetailsResult =
  | { status: "ok"; details: McpConsentDetails }
  | { status: "licenseRequired" }
  | { status: "error"; message: string };

const errorMessageSchema = z.object({ message: z.string() });

/** The authorization endpoint with the MCP client's original authorization request. */
export function mcpAuthorizationUrl(search: string): string {
  return `${baseUrl}/oauth2/authorize${search}`;
}

/** What the MCP client waiting for consent asked for, identified by the consent `state`. */
export async function getMcpConsentDetails(
  state: string,
): Promise<McpConsentDetailsResult> {
  try {
    const response = await apiFetch(
      `${baseUrl}/oauth2/consent/details?state=${encodeURIComponent(state)}`,
      { method: "GET", credentials: "include" },
    );
    if (response.status === 402) {
      return { status: "licenseRequired" };
    }
    const json: unknown = await response.json();
    if (!response.ok) {
      const error = errorMessageSchema.safeParse(json);
      return {
        status: "error",
        message: error.success ? error.data.message : "Unknown error",
      };
    }
    return { status: "ok", details: mcpConsentDetailsSchema.parse(json) };
  } catch (error) {
    return {
      status: "error",
      message: error instanceof Error ? error.message : "Unknown error",
    };
  }
}

const consentResultSchema = z.object({ redirectUri: z.string() });

/**
 * Submits the user's decision to the authorization server. It answers with where to send the
 * browser next: back to the MCP client, with an authorization code or an access_denied error.
 * Returns null if the authorization request can no longer be completed.
 */
export async function submitMcpConsent(
  clientId: string,
  state: string,
  approve: boolean,
): Promise<string | null> {
  const body = new URLSearchParams({ client_id: clientId, state });
  if (approve) {
    body.append("scope", "mcp");
  }
  try {
    const response = await apiFetch(`${baseUrl}/oauth2/authorize`, {
      method: "POST",
      credentials: "include",
      body,
    });
    if (!response.ok) {
      return null;
    }
    return consentResultSchema.parse(await response.json()).redirectUri;
  } catch {
    return null;
  }
}
