package dev.kviklet.kviklet.helper

import dev.kviklet.kviklet.service.dto.EventStreamingSettings
import dev.kviklet.kviklet.service.eventstream.EventStreamingProperties

fun eventStreamingProperties(settings: EventStreamingSettings) = EventStreamingProperties().apply {
    enabled = settings.enabled
    directory = settings.directory
    maxFileSizeMiB = settings.maxFileSizeMiB
    retentionDays = settings.retentionDays
    maxArchiveSizeMiB = settings.maxArchiveSizeMiB
    loggingLevel = settings.loggingLevel
}
