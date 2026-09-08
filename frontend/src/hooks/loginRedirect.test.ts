import { describe, expect, test } from "vitest";
import {
  loginPathFor,
  safeRedirectTarget,
  withRedirectTarget,
} from "./loginRedirect";

describe("safeRedirectTarget", () => {
  test("keeps relative paths including query and hash", () => {
    expect(safeRedirectTarget("/requests/abc?tab=1#top")).toBe(
      "/requests/abc?tab=1#top",
    );
    expect(safeRedirectTarget("/settings/users")).toBe("/settings/users");
  });

  test("falls back to the index page when nothing is given", () => {
    expect(safeRedirectTarget(null)).toBe("/");
    expect(safeRedirectTarget(undefined)).toBe("/");
    expect(safeRedirectTarget("")).toBe("/");
  });

  test("rejects anything that could leave the site", () => {
    expect(safeRedirectTarget("https://evil.example/requests")).toBe("/");
    expect(safeRedirectTarget("//evil.example")).toBe("/");
    expect(safeRedirectTarget("/\\evil.example")).toBe("/");
    expect(safeRedirectTarget("javascript:alert(1)")).toBe("/");
    expect(safeRedirectTarget("requests/abc")).toBe("/");
  });

  test("rejects the login page itself", () => {
    expect(safeRedirectTarget("/login")).toBe("/");
    expect(safeRedirectTarget("/login?redirect=%2Fx")).toBe("/");
    expect(safeRedirectTarget("/login/")).toBe("/");
    expect(safeRedirectTarget("/loginish")).toBe("/loginish");
  });
});

describe("loginPathFor", () => {
  test("encodes the full location", () => {
    expect(
      loginPathFor({ pathname: "/requests/a b", search: "?x=1", hash: "#h" }),
    ).toBe("/login?redirect=%2Frequests%2Fa%20b%3Fx%3D1%23h");
  });

  test("uses a plain login path for the index page", () => {
    expect(loginPathFor({ pathname: "/", search: "", hash: "" })).toBe(
      "/login",
    );
  });
});

describe("withRedirectTarget", () => {
  test("appends the target to an SSO start url", () => {
    expect(
      withRedirectTarget("http://api/oauth2/authorization/google", "/new"),
    ).toBe("http://api/oauth2/authorization/google?redirect=%2Fnew");
  });

  test("leaves the url alone for the index page", () => {
    expect(withRedirectTarget("http://api/saml2/authenticate/saml", "/")).toBe(
      "http://api/saml2/authenticate/saml",
    );
  });
});
