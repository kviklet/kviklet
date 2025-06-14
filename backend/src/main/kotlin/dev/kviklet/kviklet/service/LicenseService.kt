// This file is not MIT licensed
package dev.kviklet.kviklet.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.security.NoPolicy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.PublicKey
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class LicenseService(private val licenseAdapter: LicenseAdapter) {
    private val pem = this::class.java.getResource("/kviklet-key.pem")?.readText()?.trim()
        ?: throw RuntimeException("Could not load public key for license verification")

    private val mapper = jacksonObjectMapper()

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        mapper.findAndRegisterModules() // For handling LocalDateTime, etc.
    }

    @NoPolicy
    fun getLicenses(): List<License> = licenseAdapter.getLicenses().map { createLicenseFromLicenseFile(it) }

    private fun createLicenseFromLicenseFile(licenseFile: LicenseFile): License {
        val licenseDataString: String
        val signature: String
        val publicKey: PublicKey

        try {
            // Parse the entire JSON
            val rootNode = mapper.readTree(licenseFile.fileContent)
            licenseDataString = rootNode.get("license_data").toString()
            signature = rootNode.get("signature").asText()

            publicKey = LicenseVerifier().loadPublicKey(pem)
        } catch (e: Exception) {
            throw InvalidLicenseException("Error processing license file: ${e.message}", e)
        }
        if (!LicenseVerifier().verifyLicense(licenseDataString, signature, publicKey)) {
            throw InvalidLicenseException("Invalid license signature")
        }

        val licenseDataMap: Map<String, Any> = mapper.readValue(licenseDataString)
        return License(
            file = licenseFile,
            validUntil = LocalDate.parse(licenseDataMap["expiry_date"].toString()),
            createdAt = LocalDateTime.now(),
            allowedUsers = licenseDataMap["max_users"].toString().toUInt(),
        )
    }

    @Policy(Permission.CONFIGURATION_EDIT, checkIsPresentOnly = true)
    fun processLicenseFile(file: MultipartFile) {
        val licenseFileContent = file.inputStream.readBytes().toString(Charsets.UTF_8)
        val licenseFileName = file.originalFilename ?: "license.json"
        val license = createLicenseFromLicenseFile(
            LicenseFile(
                fileContent = licenseFileContent,
                fileName = licenseFileName,
                createdAt = LocalDateTime.now(),
            ),
        )

        licenseAdapter.createLicense(license.file)
    }

    @NoPolicy
    fun getActiveLicense(): License? = getLicenses().filter { it.isValid() }.maxByOrNull { it.validUntil }
}

class InvalidLicenseException(message: String, e: Exception? = null) : IllegalArgumentException(message, e)

class LicenseRestrictionException(message: String, e: Exception? = null) : RuntimeException(message, e)
