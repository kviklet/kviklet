import { describe, expect, it } from "vitest";
import { createUserRequestSchema } from "./UserApi";

const messagesFor = (input: unknown) => {
  const result = createUserRequestSchema.safeParse(input);
  if (result.success) return {};
  return Object.fromEntries(
    result.error.issues.map((issue) => [issue.path.join("."), issue.message]),
  );
};

describe("createUserRequestSchema", () => {
  it("uses the custom messages for empty fields", () => {
    expect(messagesFor({ email: "", password: "", fullName: "" })).toEqual({
      email: "Email must be between 3 and 50 characters.",
      password: "Password must be between 6 and 50 characters.",
      fullName: "Full name is required.",
    });
  });
});
