// This file is not MIT licensed
package dev.kviklet.kviklet.service.dto

import java.time.Instant

data class EventStreamingSettings(
    val enabled: Boolean = false,
    val directory: String = "/var/log/kviklet/events",
    val maxFileSizeMiB: Int = 100,
    val retentionDays: Int = 180,
    val maxArchiveSizeMiB: Int = 100,
)

data class EventStreamingStatus(
    val state: String,
    val lastWriteAt: Instant?,
    val lastError: String?,
    val detectedFailures: Long,
)

data class EventStreamingResponse(val settings: EventStreamingSettings, val status: EventStreamingStatus)
