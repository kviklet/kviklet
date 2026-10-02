// This file is not MIT licensed
import { z } from "zod";
import baseUrl from "./base";
import { fetchWithErrorHandling } from "./Errors";

export const EventStreamingSettingsSchema = z.object({
  enabled: z.boolean(),
  directory: z.string().min(1),
  maxFileSizeMiB: z.number().int().min(1).max(1024),
  retentionDays: z.number().int().min(1).max(365),
  maxArchiveSizeMiB: z.number().int().min(1).max(102400),
});
const ResponseSchema = z.object({
  settings: EventStreamingSettingsSchema,
  status: z.object({
    state: z.enum(["disabled", "active", "degraded", "license_expired"]),
    lastWriteAt: z.string().nullable(),
    lastError: z.string().nullable(),
    detectedFailures: z.number(),
  }),
});
export type EventStreamingSettings = z.infer<
  typeof EventStreamingSettingsSchema
>;
export type EventStreamingResponse = z.infer<typeof ResponseSchema>;

export const getEventStreaming = () =>
  fetchWithErrorHandling(
    `${baseUrl}/config/event-streaming`,
    {
      method: "GET",
      credentials: "include",
    },
    ResponseSchema,
  );

export const putEventStreaming = (settings: EventStreamingSettings) =>
  fetchWithErrorHandling(
    `${baseUrl}/config/event-streaming`,
    {
      method: "PUT",
      credentials: "include",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(settings),
    },
    ResponseSchema,
  );
