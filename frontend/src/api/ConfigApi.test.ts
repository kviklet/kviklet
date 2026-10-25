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

  it("parses license dates and keeps null ones null", async () => {
    respondWith({
      ...minimalConfig,
      validUntil: "2027-01-01T00:00:00Z",
      createdAt: null,
    });

    const config = await getConfig();

    expect(config).toMatchObject({
      validUntil: new Date("2027-01-01T00:00:00Z"),
      createdAt: null,
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

  it("parses release and commit dates of an update", async () => {
    respondWith({
      ...minimalConfig,
      update: {
        channel: "MAIN",
        url: "https://example.com/compare",
        behindBy: 1,
        commits: [
          {
            sha: "def5678",
            message: "Fix things",
            committedAt: "2026-10-20T12:00:00Z",
            url: "https://example.com/commit/def5678",
          },
        ],
      },
    });

    const config = await getConfig();

    expect(config).toMatchObject({
      update: {
        commits: [{ committedAt: new Date("2026-10-20T12:00:00Z") }],
      },
    });
  });
});

describe("ConfigPayloadSchema", () => {
  it("only keeps the writable settings", () => {
    const payload = ConfigPayloadSchema.parse({
      ...minimalConfig,
      teamsUrl: "https://teams.example.com/hook",
      slackUrl: null,
      proxyEnabled: true,
    });

    expect(payload).toEqual({
      teamsUrl: "https://teams.example.com/hook",
      slackUrl: null,
      proxyEnabled: true,
    });
  });

  it("leaves out toggles that were not sent instead of defaulting them", () => {
    const payload = ConfigPayloadSchema.parse({ teamsUrl: "" });

    expect(payload).toEqual({ teamsUrl: "" });
    expect(payload).not.toHaveProperty("proxyEnabled");
    expect(payload).not.toHaveProperty("mcpEnabled");
  });
});
