package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.ApplicationProperties
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
    // Public (like licenseValid) because the review page needs it for every user, to decide
    // whether the proxy action is live, grayed out, or shown as an enterprise upsell.
    open val proxyEnabled: Boolean,
    // Public for the same reason: every user is shown how to connect an MCP client while it is on.
    open val mcpEnabled: Boolean,
    open val version: String,
    open val buildDate: String,
    open val gitCommit: String,
)

// Null fields mean "leave unchanged", so a partial update never wipes the other settings.
data class ConfigRequest(
    val teamsUrl: String?,
    val slackUrl: String?,
    val proxyEnabled: Boolean? = null,
    val mcpEnabled: Boolean? = null,
)

data class ConfigResponse(
    override val oAuthProvider: String?,
    override val ldapEnabled: Boolean,
    override val samlEnabled: Boolean,
    override val licenseValid: Boolean,
    override val validUntil: LocalDate?,
    override val createdAt: LocalDateTime?,
    override val allowedUsers: UInt?,
    override val proxyEnabled: Boolean,
    override val mcpEnabled: Boolean,
    override val version: String,
    override val buildDate: String,
    override val gitCommit: String,
    val teamsUrl: String?,
    val slackUrl: String?,
) : PublicConfigResponse(
    oAuthProvider,
    ldapEnabled,
    samlEnabled,
    licenseValid,
    validUntil,
    createdAt,
    allowedUsers,
    proxyEnabled,
    mcpEnabled,
    version,
    buildDate,
    gitCommit,
) {
    companion object {
        fun fromConfiguration(
            configuration: Configuration,
            oAuthProvider: String?,
            ldapEnabled: Boolean,
            samlEnabled: Boolean,
            license: License?,
            version: String,
            buildDate: String,
            gitCommit: String,
        ): ConfigResponse = ConfigResponse(
            oAuthProvider = oAuthProvider,
            ldapEnabled = ldapEnabled,
            samlEnabled = samlEnabled,
            licenseValid = license?.isValid() == true,
            validUntil = license?.validUntil,
            createdAt = license?.createdAt,
            allowedUsers = license?.allowedUsers,
            proxyEnabled = configuration.proxyEnabled ?: false,
            mcpEnabled = configuration.mcpEnabled ?: true,
            version = version,
            buildDate = buildDate,
            gitCommit = gitCommit,
            teamsUrl = configuration.teamsUrl,
            slackUrl = configuration.slackUrl,
        )
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
    val applicationProperties: ApplicationProperties,
) {

    @GetMapping("/")
    fun getConfig(): PublicConfigResponse {
        val license = licenseService.getDisplayedLicense()
        try {
            val config = configService.getConfiguration()
            return ConfigResponse.fromConfiguration(
                config,
                identityProviderProperties.type?.lowercase(),
                ldapProperties.enabled,
                samlProperties.isSamlEnabled(),
                license,
                applicationProperties.version,
                applicationProperties.buildDate,
                applicationProperties.gitCommit,
            )
        } catch (e: AccessDeniedException) {
            return PublicConfigResponse(
                oAuthProvider = identityProviderProperties.type?.lowercase(),
                ldapEnabled = ldapProperties.enabled,
                samlEnabled = samlProperties.isSamlEnabled(),
                licenseValid = license?.isValid() == true,
                validUntil = license?.validUntil,
                createdAt = license?.createdAt,
                allowedUsers = license?.allowedUsers,
                proxyEnabled = configService.isProxyEnabled(),
                mcpEnabled = configService.isMcpEnabled(),
                version = applicationProperties.version,
                buildDate = applicationProperties.buildDate,
                gitCommit = applicationProperties.gitCommit,
            )
        }
    }

    @PutMapping("/")
    fun createConfig(@RequestBody request: ConfigRequest): ConfigResponse {
        val license = licenseService.getDisplayedLicense()
        val config = configService.setConfiguration(
            Configuration(
                teamsUrl = request.teamsUrl,
                slackUrl = request.slackUrl,
                proxyEnabled = request.proxyEnabled,
                mcpEnabled = request.mcpEnabled,
            ),
        )
        return ConfigResponse.fromConfiguration(
            config,
            identityProviderProperties.type?.lowercase(),
            ldapProperties.enabled,
            samlProperties.isSamlEnabled(),
            license,
            applicationProperties.version,
            applicationProperties.buildDate,
            applicationProperties.gitCommit,
        )
    }

    @PostMapping("/license/")
    fun uploadLicense(@RequestParam("file") file: MultipartFile): ResponseEntity<Any> {
        // Call service to handle file
        licenseService.processLicenseFile(file)
        return ResponseEntity.ok("License file uploaded successfully")
    }
}
