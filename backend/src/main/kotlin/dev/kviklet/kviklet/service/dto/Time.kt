package dev.kviklet.kviklet.service.dto

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

fun utcTimeNow(): LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime()
