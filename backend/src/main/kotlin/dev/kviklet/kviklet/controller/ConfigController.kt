package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.IdentityProviderProperties
import dev.kviklet.kviklet.security.ldap.LdapProperties
import dev.kviklet.kviklet.security.saml.SamlProperties
import dev.kviklet.kviklet.service.ConfigService
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.dto.Configuration
import dev.kviklet.kviklet.service.dto.License
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDate
import java.time.LocalDateTime

open class PublicConfigResponse(
    open val oAuthProvider: String?,
    open val ldapEnabled: Boolean,
    open val samlEnabled: Boolean,
    open val licenseValid: Boolean,
    open val validUntil: LocalDate?,
    open val createdAt: LocalDateTime?,
    open val allowedUsers: UInt?,
)

data class ConfigRequest(val teamsUrl: String?, val slackUrl: String?)

data class ConfigResponse(
    override val oAuthProvider: String?,
    override val ldapEnabled: Boolean,
    override val samlEnabled: Boolean,
    override val licenseValid: Boolean,
    override val validUntil: LocalDate?,
    override val createdAt: LocalDateTime?,
    override val allowedUsers: UInt?,
    val teamsUrl: String?,
    val slackUrl: String?,
) : PublicConfigResponse(oAuthProvider, ldapEnabled, samlEnabled, licenseValid, validUntil, createdAt, allowedUsers) {
    companion object {
        fun fromConfiguration(
            configuration: Configuration,
            oAuthProvider: String?,
            ldapEnabled: Boolean,
            samlEnabled: Boolean,
            licenses: List<License>,
        ): ConfigResponse {
            val licensesSorted = licenses.sortedByDescending { it.file.createdAt }
            return ConfigResponse(
                oAuthProvider = oAuthProvider,
                ldapEnabled = ldapEnabled,
                samlEnabled = samlEnabled,
                licenseValid = licenses.any { it.isValid() },
                validUntil = licensesSorted.firstOrNull()?.validUntil,
                createdAt = licensesSorted.firstOrNull()?.createdAt,
                allowedUsers = licensesSorted.firstOrNull()?.allowedUsers,
                teamsUrl = configuration.teamsUrl,
                slackUrl = configuration.slackUrl,
            )
        }
    }
}

@RestController
@Validated
@RequestMapping("/config")
@Tag(
    name = "Config",
    description = "Configure Kviklet in general.",
)
class ConfigController(
    val identityProviderProperties: IdentityProviderProperties,
    val ldapProperties: LdapProperties,
    val samlProperties: SamlProperties,
    val configService: ConfigService,
    val licenseService: LicenseService,
) {

    @GetMapping("/")
    fun getConfig(): PublicConfigResponse {
        val licenses = licenseService.getLicenses()
        val licensesSorted = licenses.sortedByDescending { it.file.createdAt }
        try {
            val config = configService.getConfiguration()
            return ConfigResponse.fromConfiguration(
                config,
                identityProviderProperties.type?.lowercase(),
                ldapProperties.enabled,
                samlProperties.isSamlEnabled(),
                licenses,
            )
        } catch (e: AccessDeniedException) {
            return PublicConfigResponse(
                oAuthProvider = identityProviderProperties.type?.lowercase(),
                ldapEnabled = ldapProperties.enabled,
                samlEnabled = samlProperties.isSamlEnabled(),
                licenseValid = licenses.any { it.isValid() },
                validUntil = licensesSorted.firstOrNull()?.validUntil,
                createdAt = licensesSorted.firstOrNull()?.createdAt,
                allowedUsers = licensesSorted.firstOrNull()?.allowedUsers,
            )
        }
    }

    @PutMapping("/")
    fun createConfig(@RequestBody request: ConfigRequest): ConfigResponse {
        val licenses = licenseService.getLicenses()
        val config = configService.setConfiguration(
            Configuration(
                teamsUrl = request.teamsUrl,
                slackUrl = request.slackUrl,
            ),
        )
        return ConfigResponse.fromConfiguration(
            config,
            identityProviderProperties.type?.lowercase(),
            ldapProperties.enabled,
            samlProperties.isSamlEnabled(),
            licenses,
        )
    }

    @PostMapping("/license/")
    fun uploadLicense(@RequestParam("file") file: MultipartFile): ResponseEntity<Any> {
        // Call service to handle file
        licenseService.processLicenseFile(file)
        return ResponseEntity.ok("License file uploaded successfully")
    }
}
