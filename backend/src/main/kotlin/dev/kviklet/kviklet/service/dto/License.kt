package dev.kviklet.kviklet.service.dto

import java.time.LocalDate
import java.time.LocalDateTime

data class License(
    val file: LicenseFile,
    val validUntil: LocalDate,
    val createdAt: LocalDateTime,
    val allowedUsers: UInt,
) {
    fun isValid(): Boolean {
        return validUntil.isAfter(LocalDate.now())
    }
}

data class LicenseFile(
    val fileContent: String,
    val fileName: String,
    val createdAt: LocalDateTime,
)
