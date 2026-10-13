package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.LicenseFile
import jakarta.persistence.Entity
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(name = "license")
class LicenseEntity(
    var fileContent: String,
    var fileName: String,
    var createdAt: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime(),
) : BaseEntity() {
    fun toDto(): LicenseFile = LicenseFile(
        fileContent = fileContent,
        fileName = fileName,
        createdAt = createdAt,
    )
}

interface LicenseRepository : JpaRepository<LicenseEntity, String>

/** Published after the stored licenses changed, so cached license data can be dropped. */
object LicensesChanged

@Service
class LicenseAdapter(
    private val licenseRepository: LicenseRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    fun getLicenses(): List<LicenseFile> = licenseRepository.findAll().map { it.toDto() }

    fun createLicense(license: LicenseFile): LicenseFile = licenseRepository.save(
        LicenseEntity(
            fileContent = license.fileContent,
            fileName = license.fileName,
        ),
    ).toDto().also { eventPublisher.publishEvent(LicensesChanged) }

    fun deleteAll() {
        licenseRepository.deleteAll()
        eventPublisher.publishEvent(LicensesChanged)
    }
}
