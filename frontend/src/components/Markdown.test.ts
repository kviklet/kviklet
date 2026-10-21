import { describe, expect, it } from "vitest";
import { shortGithubLinkText } from "./Markdown";

describe("shortGithubLinkText", () => {
  it("shortens pull requests, issues and comparisons of this repository", () => {
    expect(
      shortGithubLinkText("https://github.com/kviklet/kviklet/pull/585"),
    ).toBe("#585");
    expect(
      shortGithubLinkText("https://github.com/kviklet/kviklet/issues/12"),
    ).toBe("#12");
    expect(
      shortGithubLinkText(
        "https://github.com/kviklet/kviklet/compare/0.9.1...0.9.2",
      ),
    ).toBe("0.9.1...0.9.2");
  });

  it("keeps the repository name for other repositories", () => {
    expect(shortGithubLinkText("https://github.com/someone/fork/pull/3")).toBe(
      "someone/fork#3",
    );
  });

  it("leaves every other link alone", () => {
    expect(
      shortGithubLinkText("https://github.com/kviklet/kviklet/pull/585/files"),
    ).toBeNull();
    expect(shortGithubLinkText("https://kviklet.dev/docs")).toBeNull();
  });
});
