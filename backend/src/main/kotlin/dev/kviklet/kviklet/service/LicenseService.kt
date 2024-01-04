package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.service.dto.License
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class LicenseService(
    val licenseAdapter: LicenseAdapter,
) {
    fun getLicenses(): List<License> {
        return licenseAdapter.getLicenses()
    }

    fun createLicense(licenseKey: String): License {
        return licenseAdapter.createLicense(
            License(
                licenseKey = licenseKey,
                allowedUsers = 20u,
                validUntil = LocalDateTime.now().plusDays(30),
                createdAt = LocalDateTime.now(),
            ),
        )
    }
}
