package dev.kviklet.kviklet.db

import dev.kviklet.kviklet.db.util.BaseEntity
import dev.kviklet.kviklet.service.dto.LicenseFile
import jakarta.persistence.Entity
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
    fun toDto(): LicenseFile {
        return LicenseFile(
            fileContent = fileContent,
            fileName = fileName,
            createdAt = createdAt,
        )
    }
}

interface LicenseRepository : JpaRepository<LicenseEntity, String>

@Service
class LicenseAdapter(
    private val licenseRepository: LicenseRepository,
) {
    fun getLicenses(): List<LicenseFile> {
        return licenseRepository.findAll().map { it.toDto() }
    }

    fun createLicense(license: LicenseFile): LicenseFile {
        return licenseRepository.save(
            LicenseEntity(
                fileContent = license.fileContent,
                fileName = license.fileName,
            ),
        ).toDto()
    }

    fun deleteAll() {
        licenseRepository.deleteAll()
    }
}
