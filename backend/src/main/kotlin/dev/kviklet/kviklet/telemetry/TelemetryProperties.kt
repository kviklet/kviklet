package dev.kviklet.kviklet.telemetry

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Anonymous usage telemetry. The events Kviklet reports are the [TelemetryEvent] subclasses;
 * `KVIKLET_TELEMETRY_ENABLED=false` switches all of it off.
 */
@Component
@ConfigurationProperties(prefix = "kviklet.telemetry")
class TelemetryProperties {
    var enabled: Boolean = true

    /** Events queued beyond this are dropped rather than held in memory. */
    var maxQueueSize: Int = 1000
}
