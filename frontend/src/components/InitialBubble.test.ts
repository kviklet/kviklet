import { describe, it, expect } from "vitest";
import { avatarColorFromName } from "./InitialBubble";

describe("avatarColorFromName", () => {
  it("is deterministic for the same name", () => {
    expect(avatarColorFromName("Jascha Beste")).toBe(
      avatarColorFromName("Jascha Beste"),
    );
  });

  it("gives different colors to names that used to collide", () => {
    expect(avatarColorFromName("Dan Nguyen")).not.toBe(
      avatarColorFromName("Jascha Beste"),
    );
  });

  it("handles an empty name", () => {
    expect(avatarColorFromName("")).toMatch(/^bg-/);
  });
});
