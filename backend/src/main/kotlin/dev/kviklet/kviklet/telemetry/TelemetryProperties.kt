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

    /** Events queued beyond this are dropped rather than held in memory. */
    var maxQueueSize: Int = 1000

    class PostHog {
        var host: String = "https://eu.i.posthog.com"

        /** The public, write-only project key. Blank disables telemetry. */
        var key: String = ""
    }
}
