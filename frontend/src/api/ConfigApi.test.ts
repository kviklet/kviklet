import { afterEach, describe, expect, it, vi } from "vitest";
import { ConfigPayloadSchema, getConfig } from "./ConfigApi";

const minimalConfig = {
  ldapEnabled: false,
  samlEnabled: false,
  licenseValid: true,
  version: "1.2.3",
  buildDate: "2026-10-01",
  gitCommit: "abc1234",
};

const respondWith = (body: unknown) =>
  vi.stubGlobal(
    "fetch",
    vi.fn(() => Promise.resolve(Response.json(body))),
  );

describe("getConfig", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("fills in defaults for flags an older backend leaves out", async () => {
    respondWith(minimalConfig);

    const config = await getConfig();

    expect(config).toEqual({
      ...minimalConfig,
      proxyEnabled: false,
      mcpEnabled: true,
    });
  });

  it("defaults the release and commit lists of an update", async () => {
    respondWith({
      ...minimalConfig,
      update: { channel: "RELEASE", url: "https://example.com/releases" },
    });

    const config = await getConfig();

    expect(config).toMatchObject({
      update: {
        channel: "RELEASE",
        url: "https://example.com/releases",
        releases: [],
        commits: [],
      },
    });
  });
});

describe("ConfigPayloadSchema", () => {
  it("leaves out toggles that were not sent instead of defaulting them", () => {
    const payload = ConfigPayloadSchema.parse({ teamsUrl: "" });

    expect(payload).toEqual({ teamsUrl: "" });
    expect(payload).not.toHaveProperty("proxyEnabled");
    expect(payload).not.toHaveProperty("mcpEnabled");
  });
});
