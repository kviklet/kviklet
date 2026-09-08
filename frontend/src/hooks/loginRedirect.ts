import { useSearchParams } from "react-router-dom";

/**
 * Query parameter on /login (and on the SSO start endpoints of the backend) that names the
 * page to open once the user is logged in, e.g. `/login?redirect=%2Frequests%2Fabc`.
 */
const REDIRECT_PARAM = "redirect";

const LOGIN_PATH = /^\/login(?:[/?#]|$)/;

/**
 * Validates a redirect target taken from the URL. Only same-origin paths are accepted: a
 * relative path starting with a single slash. Protocol-relative URLs ("//evil.example"),
 * backslash variants and absolute URLs fall back to "/" so the login page can never be used
 * as an open redirector. The login page itself is rejected to avoid a redirect loop.
 */
function safeRedirectTarget(raw: string | null | undefined): string {
  if (
    !raw ||
    !raw.startsWith("/") ||
    raw.startsWith("//") ||
    raw.startsWith("/\\") ||
    LOGIN_PATH.test(raw)
  ) {
    return "/";
  }
  return raw;
}

/**
 * The login URL that brings the user back to `location` afterwards. The index page is the
 * default landing anyway, so it gets a plain /login.
 */
function loginPathFor(location: {
  pathname: string;
  search: string;
  hash: string;
}): string {
  const target = location.pathname + location.search + location.hash;
  if (target === "/") {
    return "/login";
  }
  return `/login?${REDIRECT_PARAM}=${encodeURIComponent(target)}`;
}

/**
 * Appends the redirect target to an SSO start URL so the backend can send the user back to
 * it after the round trip to the identity provider.
 */
function withRedirectTarget(url: string, target: string): string {
  if (target === "/") {
    return url;
  }
  return `${url}?${REDIRECT_PARAM}=${encodeURIComponent(target)}`;
}

/** The validated page to send the user to after logging in. */
function useLoginRedirectTarget(): string {
  const [searchParams] = useSearchParams();
  return safeRedirectTarget(searchParams.get(REDIRECT_PARAM));
}

export {
  REDIRECT_PARAM,
  safeRedirectTarget,
  loginPathFor,
  withRedirectTarget,
  useLoginRedirectTarget,
};
