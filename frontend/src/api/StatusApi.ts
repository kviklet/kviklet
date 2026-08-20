import { z } from "zod";
import baseUrl, { apiFetch } from "./base";
import { oncallGrantResponseSchema } from "./UserApi";

const StatusResponse = z.object({
  email: z.string(),
  fullName: z.string().optional(),
  status: z.string(),
  id: z.string(),
  licenseValid: z.boolean(),
  /**
   * Permissions the user holds on at least one resource, already resolved by the backend. Good for
   * hiding what a role can never use; never for deciding whether the user may act on a particular
   * connection or request — use that object's own `permissions` for that.
   */
  permissions: z.array(z.string()),
  activeOncallGrant: oncallGrantResponseSchema.nullable().optional(),
  pendingOncallGrant: oncallGrantResponseSchema.nullable().optional(),
  canManageOncall: z.boolean().optional().default(false),
});

type StatusResponse = z.infer<typeof StatusResponse>;

const checklogin = async (): Promise<StatusResponse | false> => {
  const response = await apiFetch(baseUrl + "/status", {
    method: "GET",
    credentials: "include",
  });
  if (response.status != 200) {
    return false;
  }
  const json: unknown = await response.json();
  return StatusResponse.parse(json);
};

export { checklogin };
export type { StatusResponse };
