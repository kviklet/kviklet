package dev.kviklet.kviklet.telemetry

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * Anonymous usage telemetry. Everything Kviklet reports is listed in the README under "Telemetry";
 * `KVIKLET_TELEMETRY_ENABLED=false` switches all of it off.
 */
@Component
@ConfigurationProperties(prefix = "kviklet.telemetry")
class TelemetryProperties {
    var enabled: Boolean = true
    var posthog: PostHog = PostHog()

    /** Seconds between flushes of queued events. */
    var flushIntervalSeconds: Long = 10

    /** Events queued beyond this are dropped rather than held in memory. */
    var maxQueueSize: Int = 1000

    /** Delay before the first heartbeat, so the domain has usually been observed by then. */
    var heartbeatInitialDelay: String = "PT10M"

    class PostHog {
        var host: String = "https://eu.i.posthog.com"

        /** The public, write-only project key. Blank disables telemetry. */
        var key: String = ""
    }
}
