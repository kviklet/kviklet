import { z } from "zod";
import baseUrl from "./base";

const StatusResponse = z.object({
  email: z.string(),
  fullName: z.string().optional(),
  status: z.string(),
  id: z.string(),
});

type StatusResponse = z.infer<typeof StatusResponse>;

const checklogin = async (): Promise<StatusResponse | false> => {
  const response = await fetch(baseUrl + "/status", {
    method: "GET",
    credentials: "include",
  });
  if (response.status != 200) {
    return false;
  }
  const json: unknown = await response.json();
  const parsedResponse = StatusResponse.parse(json);
  return parsedResponse;
};

export { checklogin };
export type { StatusResponse };
