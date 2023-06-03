import { z } from "zod";

const StatusResponse = z.object({
  username: z.string(),
  status: z.string(),
});

type StatusResponse = z.infer<typeof StatusResponse>;

const checklogin = async (): Promise<string | false> => {
  const response = await fetch("http://localhost:8080/status", {
    method: "GET",
    credentials: "include",
  });
  if (response.status != 200) {
    return false;
  }
  const json = await response.json();
  const parsedResponse = StatusResponse.parse(json);
  return parsedResponse.username;
};

export { checklogin };
