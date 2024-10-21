package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.IdentityProviderProperties
import dev.kviklet.kviklet.security.LdapProperties
import dev.kviklet.kviklet.service.ConfigService
import dev.kviklet.kviklet.service.dto.Configuration
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.AccessDeniedException
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

open class PublicConfigResponse(open val oAuthProvider: String?, open val ldapEnabled: Boolean)

data class ConfigRequest(val teamsUrl: String?, val slackUrl: String?, val liveSessionEnabled: Boolean?)

data class ConfigResponse(
    override val oAuthProvider: String?,
    override val ldapEnabled: Boolean,
    val teamsUrl: String?,
    val slackUrl: String?,
    val liveSessionEnabled: Boolean,
) : PublicConfigResponse(oAuthProvider, ldapEnabled) {
    companion object {
        fun fromConfiguration(
            configuration: Configuration,
            oAuthProvider: String?,
            ldapEnabled: Boolean,
        ): ConfigResponse = ConfigResponse(
            oAuthProvider = oAuthProvider,
            ldapEnabled = ldapEnabled,
            teamsUrl = configuration.teamsUrl,
            slackUrl = configuration.slackUrl,
            liveSessionEnabled = configuration.liveSessionEnabled ?: false,
        )
    }
}

@RestController
@Validated
@RequestMapping("/config")
@Tag(
    name = "Controller",
    description = "Configure Kviklet in general.",
)
class ConfigController(
    val identityProviderProperties: IdentityProviderProperties,
    val ldapProperties: LdapProperties,
    val configService: ConfigService,
) {

    @GetMapping("/")
    fun getConfig(): PublicConfigResponse {
        try {
            val config = configService.getConfiguration()
            return ConfigResponse.fromConfiguration(
                config,
                identityProviderProperties.type?.lowercase(),
                ldapProperties.enabled,
            )
        } catch (e: AccessDeniedException) {
            return PublicConfigResponse(
                oAuthProvider = identityProviderProperties.type?.lowercase(),
                ldapEnabled = ldapProperties.enabled,
            )
        }
    }

    @PutMapping("/")
    fun createConfig(@RequestBody request: ConfigRequest): ConfigResponse {
        val config = configService.setConfiguration(
            Configuration(
                teamsUrl = request.teamsUrl,
                slackUrl = request.slackUrl,
                liveSessionEnabled = request.liveSessionEnabled,
            ),
        )
        return ConfigResponse.fromConfiguration(
            config,
            identityProviderProperties.type?.lowercase(),
            ldapProperties.enabled,
        )
    }
}
