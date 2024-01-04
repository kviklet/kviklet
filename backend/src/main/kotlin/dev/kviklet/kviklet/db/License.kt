package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.License
import jakarta.persistence.Entity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(name = "license")
class LicenseEntity(
    var licenseKey: String,
    var createdAt: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime(),
) : BaseEntity() {
    fun toDto(): License {
        return License(
            licenseKey = licenseKey,
            createdAt = createdAt,
            validUntil = LocalDateTime.now().plusDays(30),
            allowedUsers = 20u,
        )
    }
}

interface LicenseRepository : JpaRepository<LicenseEntity, String>

@Service
class LicenseAdapter(
    private val licenseRepository: LicenseRepository,
) {
    fun getLicenses(): List<License> {
        return licenseRepository.findAll().map { it.toDto() }
    }

    fun createLicense(license: License): License {
        return licenseRepository.save(LicenseEntity(licenseKey = license.licenseKey)).toDto()
    }
}
