import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import UpdateBanner from "./UpdateBanner";
import { ConfigResponse, UpdateInfo } from "../api/ConfigApi";

const releaseUpdate: UpdateInfo = {
  channel: "RELEASE",
  latestVersion: "0.9.2",
  releases: [
    {
      tag: "0.9.2",
      name: "0.9.2",
      body: "Security fixes\n\n## What's Changed\n* Fix live sessions in https://github.com/kviklet/kviklet/pull/593",
      publishedAt: new Date("2026-09-29T00:15:52Z"),
      url: "https://github.com/kviklet/kviklet/releases/tag/0.9.2",
    },
    {
      tag: "0.9.1",
      name: "0.9.1",
      body: "IAM auth in the proxy",
      publishedAt: new Date("2026-09-27T09:09:56Z"),
      url: "https://github.com/kviklet/kviklet/releases/tag/0.9.1",
    },
  ],
  commits: [],
  url: "https://github.com/kviklet/kviklet/releases/tag/0.9.2",
};

const mainUpdate: UpdateInfo = {
  channel: "MAIN",
  latestVersion: null,
  releases: [],
  behindBy: 23,
  commits: [
    {
      sha: "d8242a1a0000000000000000000000000000beef",
      message: "Show the license in force in the config (#601)",
      committedAt: new Date("2026-10-20T10:00:00Z"),
      url: "https://github.com/kviklet/kviklet/commit/d8242a1a",
    },
  ],
  url: "https://github.com/kviklet/kviklet/compare/abc1234...main",
};

const config: Partial<ConfigResponse> = {};

vi.mock("./ConfigProvider", () => ({
  default: () => ({ config }),
}));

describe("UpdateBanner", () => {
  beforeEach(() => {
    localStorage.clear();
    config.version = "0.9.0";
    config.update = releaseUpdate;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("shows nothing while the instance is up to date", () => {
    config.update = null;
    render(<UpdateBanner />);
    expect(screen.queryByTestId("update-banner")).not.toBeInTheDocument();
  });

  it("names the newest release and lists the notes of every newer one once expanded", () => {
    render(<UpdateBanner />);
    expect(screen.getByTestId("update-banner")).toHaveTextContent(
      "Kviklet 0.9.2 is available. This instance runs 0.9.0, 2 releases behind.",
    );
    expect(screen.queryByTestId("update-changelog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByTestId("update-banner-expand"));

    expect(screen.getAllByTestId("update-release")).toHaveLength(2);
    expect(screen.getByText("What's Changed")).toBeVisible();
    // Bare pull request links read "#593", like on GitHub and in the commit list.
    expect(screen.getByRole("link", { name: "#593" })).toHaveAttribute(
      "href",
      "https://github.com/kviklet/kviklet/pull/593",
    );
  });

  it("shows how far a main build is behind and the commit messages", () => {
    config.version = "dev";
    config.update = mainUpdate;
    render(<UpdateBanner />);
    expect(screen.getByTestId("update-banner")).toHaveTextContent(
      "This build is 23 commits behind main.",
    );

    fireEvent.click(screen.getByTestId("update-banner-expand"));

    expect(screen.getByTestId("update-commit")).toHaveTextContent(
      "Show the license in force in the config (#601)",
    );
    expect(screen.getByRole("link", { name: "#601" })).toHaveAttribute(
      "href",
      "https://github.com/kviklet/kviklet/pull/601",
    );
    expect(
      screen.getByRole("link", { name: "and 22 older commits on GitHub" }),
    ).toHaveAttribute("href", mainUpdate.url);
  });

  it("renders HTML and script links in release notes inertly", () => {
    config.update = {
      ...releaseUpdate,
      releases: [
        {
          ...releaseUpdate.releases[0],
          body: '<script>window.pwned = true</script><img src=x onerror="window.pwned = true">\n\n[click](javascript:window.pwned=true)',
        },
      ],
    };
    const { container } = render(<UpdateBanner />);
    fireEvent.click(screen.getByTestId("update-banner-expand"));

    expect(container.querySelector("script")).toBeNull();
    expect(container.querySelector("img")).toBeNull();
    // react-markdown replaces unsafe link targets with an inert one.
    expect(screen.getByRole("link", { name: "click" })).toHaveAttribute(
      "href",
      "javascript:void(0)",
    );
    expect((window as { pwned?: boolean }).pwned).toBeUndefined();
  });

  it("stays away for a week after the X, then comes back", () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-10-21T12:00:00Z"));
    const first = render(<UpdateBanner />);
    fireEvent.click(screen.getByTestId("update-banner-dismiss"));
    expect(screen.queryByTestId("update-banner")).not.toBeInTheDocument();
    first.unmount();

    vi.setSystemTime(new Date("2026-10-27T12:00:00Z"));
    const sixDaysLater = render(<UpdateBanner />);
    expect(screen.queryByTestId("update-banner")).not.toBeInTheDocument();
    sixDaysLater.unmount();

    vi.setSystemTime(new Date("2026-10-28T12:00:01Z"));
    render(<UpdateBanner />);
    expect(screen.getByTestId("update-banner")).toBeInTheDocument();
  });

  it("comes back right away when a newer version is out", () => {
    const first = render(<UpdateBanner />);
    fireEvent.click(screen.getByTestId("update-banner-dismiss"));
    first.unmount();

    config.update = { ...releaseUpdate, latestVersion: "0.9.3" };
    render(<UpdateBanner />);
    expect(screen.getByTestId("update-banner")).toBeInTheDocument();
  });
});
