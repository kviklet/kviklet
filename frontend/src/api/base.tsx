import { z } from "zod";

// eslint-disable-next-line @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access
const apiBasePath = import.meta.env.VITE_API_BASE_PATH;

let baseUrl = "";
if (!apiBasePath) {
  // In dev mode locally, usually spring boot runs on 8080
  baseUrl = `${window.location.protocol}//${window.location.hostname}:8080`;
} else {
  baseUrl = `${window.location.protocol}//${window.location.host}${apiBasePath}`;
}

export default baseUrl;

function withType<T, U extends string>(schema: z.ZodSchema<T>, typeValue: U) {
  return schema.transform((data) => ({
    ...data,
    _type: typeValue,
  })) as z.ZodSchema<T & { _type: U }>;
}

export { withType };
