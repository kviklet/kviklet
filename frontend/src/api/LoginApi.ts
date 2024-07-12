import { z } from "zod";
import { ApiResponse, fetchWithErrorHandling } from "./Errors";
import baseUrl from "./base";

const loginResponseSchema = z.object({
  sessionId: z.string(),
});

type LoginResponse = z.infer<typeof loginResponseSchema>;

const attemptLogin = async (
  email: string,
  password: string,
): Promise<ApiResponse<LoginResponse>> => {
  return fetchWithErrorHandling(
    `${baseUrl}/login`,
    {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        email: email,
        password: password,
      }),
    },
    loginResponseSchema,
  );
};

const logout = async (): Promise<void> => {
  await fetch(`${baseUrl}/logout`, {
    method: "POST",
    credentials: "include",
  });
};

export { attemptLogin, logout };
