// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.core

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date

// When temporaryAccessDuration is null, it indicates infinite access. This constant represents that case.
val INFINITE_ACCESS = -1L

// startTime is a UTC LocalDateTime (utcTimeNow() / an event's stored createdAt), so it must be resolved to
// an instant as UTC. Using the host's zone made a finite access window last (UTC offset) hours too long on a
// negative-offset host and fire immediately -- killing every finite session on arrival -- on a positive one.
fun getShutdownDate(startTime: LocalDateTime, maxTimeMinutes: Long): Date = Date.from(
    startTime
        .plusMinutes(maxTimeMinutes)
        .atZone(ZoneOffset.UTC)
        .toInstant(),
)
