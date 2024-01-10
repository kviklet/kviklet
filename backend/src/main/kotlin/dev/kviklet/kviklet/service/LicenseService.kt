package dev.kviklet.kviklet.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.kviklet.kviklet.db.LicenseAdapter
import dev.kviklet.kviklet.service.dto.License
import dev.kviklet.kviklet.service.dto.LicenseFile
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Service
class LicenseService(
    val licenseAdapter: LicenseAdapter,
) {
    private val pem = this::class.java.getResource("/kviklet-key.pem")?.readText()?.trim()
        ?: throw RuntimeException("Could not load public key for license verification")

    private val mapper = jacksonObjectMapper()

    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun init() {
        mapper.findAndRegisterModules() // For handling LocalDateTime, etc.
    }
    fun loadPublicKey(): PublicKey {
        val pemContent = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "") // Remove whitespace characters (new lines, spaces, tabs, etc.)

        val keyBytes = Base64.getMimeDecoder().decode(pemContent)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(spec)
    }

    fun verifyLicense(licenseKey: String, signature: String, publicKey: PublicKey): Boolean {
        try {
            val signatureBytes = Base64.getDecoder().decode(signature)
            val licenseDataJson = licenseKey.toByteArray()

            val sig = Signature.getInstance("RSASSA-PSS")
            val pssParams = PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 20, 1)
            sig.setParameter(pssParams)
            sig.initVerify(publicKey)
            sig.update(licenseDataJson)

            return sig.verify(signatureBytes)
        } catch (e: Exception) {
            logger.error("Error verifying license: ${e.message}", e)
            return false
        }
    }

    fun getLicenses(): List<License> {
        return licenseAdapter.getLicenses().map { createLicenseFromLicenseFile(it) }
    }

    fun createLicenseFromLicenseFile(licenseFile: LicenseFile): License {
        val licenseDataString: String
        val signature: String
        val publicKey: PublicKey

        try {
            // Parse the entire JSON
            val rootNode = mapper.readTree(licenseFile.fileContent)
            licenseDataString = rootNode.get("license_data").toString()
            signature = rootNode.get("signature").asText()

            publicKey = loadPublicKey()
        } catch (e: Exception) {
            throw InvalidLicenseException("Error processing license file: ${e.message}", e)
        }
        if (!verifyLicense(licenseDataString, signature, publicKey)) {
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
}

class InvalidLicenseException(message: String, e: Exception? = null) : IllegalArgumentException(message, e)
