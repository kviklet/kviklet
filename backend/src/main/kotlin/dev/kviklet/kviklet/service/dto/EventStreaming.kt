// This file is not MIT licensed
package dev.kviklet.kviklet.service.dto

import com.fasterxml.jackson.annotation.JsonCreator
import java.time.Instant

enum class EventLoggingLevel {
    SECURITY_ONLY,
    WITHOUT_QUERY_TEXT,
    FULL,
    ;

    companion object {
        // Reject numeric enum ordinals: API clients must use the documented level names.
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): EventLoggingLevel = valueOf(value)
    }
}

data class EventStreamingSettings(
    val enabled: Boolean = false,
    val directory: String = "/var/log/kviklet/events",
    val maxFileSizeMiB: Int = 10,
    val retentionDays: Int = 180,
    val maxArchiveSizeMiB: Int = 100,
    val loggingLevel: EventLoggingLevel = EventLoggingLevel.FULL,
)

data class EventStreamingStatus(
    val state: String,
    val lastWriteAt: Instant?,
    val lastError: String?,
    val detectedFailures: Long,
)

data class EventStreamingResponse(val settings: EventStreamingSettings, val status: EventStreamingStatus)
