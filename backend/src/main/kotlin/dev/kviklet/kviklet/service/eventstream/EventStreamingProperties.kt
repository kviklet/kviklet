// This file is not MIT licensed
package dev.kviklet.kviklet.service.eventstream

import dev.kviklet.kviklet.service.dto.EventLoggingLevel
import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.nio.file.Path

@Component
@ConfigurationProperties(prefix = "kviklet.event-streaming")
class EventStreamingProperties {
    var enabled: Boolean = false
    var directory: String = "/var/log/kviklet/events"
    var maxFileSizeMiB: Int = 10
    var retentionDays: Int = 180
    var maxArchiveSizeMiB: Int = 100
    var loggingLevel: EventLoggingLevel = EventLoggingLevel.FULL

    /** Validate at startup and give the service an immutable snapshot. */
    fun toSettings(): EventStreamingSettings {
        require(maxFileSizeMiB in 1..1024) { "kviklet.event-streaming.max-file-size-mib must be between 1 and 1024" }
        require(retentionDays in 1..365) { "kviklet.event-streaming.retention-days must be between 1 and 365" }
        require(maxArchiveSizeMiB in (maxFileSizeMiB + 1)..102400) {
            "kviklet.event-streaming.max-archive-size-mib must be at least 1 MiB larger than max-file-size-mib and at most 102400"
        }
        require(Path.of(directory).isAbsolute) { "kviklet.event-streaming.directory must be an absolute path" }
        return EventStreamingSettings(
            enabled,
            directory,
            maxFileSizeMiB,
            retentionDays,
            maxArchiveSizeMiB,
            loggingLevel,
        )
    }
}
