package dev.kviklet.kviklet.service.dto

import java.time.LocalDateTime

data class License(
    val licenseKey: String,
    val validUntil: LocalDateTime,
    val createdAt: LocalDateTime,
    val allowedUsers: UInt,
) {
    fun isValid(): Boolean {
        return validUntil.isAfter(LocalDateTime.now())
    }
}
