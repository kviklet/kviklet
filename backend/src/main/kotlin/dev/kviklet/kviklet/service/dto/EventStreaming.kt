// This file is not MIT licensed
package dev.kviklet.kviklet.service.dto

import java.time.Instant

enum class EventLoggingLevel {
    SECURITY_ONLY,
    WITHOUT_QUERY_TEXT,
    FULL,
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
