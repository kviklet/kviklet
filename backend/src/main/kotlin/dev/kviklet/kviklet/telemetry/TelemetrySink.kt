package dev.kviklet.kviklet.telemetry

import java.time.Instant

/** One fully enriched event, ready to be sent. */
data class TelemetryPayload(
    val event: String,
    val distinctId: String,
    val properties: Map<String, Any?>,
    val timestamp: Instant,
)

/** Where enriched events go. [PostHogSink] is the real one; tests substitute their own. */
interface TelemetrySink {
    fun send(payload: TelemetryPayload)
}
