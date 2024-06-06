package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.IdentityProviderProperties
import dev.kviklet.kviklet.service.ConfigService
import dev.kviklet.kviklet.service.dto.Configuration
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

open class PublicConfigResponse(
    open val oAuthProvider: String?,
)

data class ConfigRequest(
    val host: String?,
    val teamsUrl: String?,
    val slackUrl: String?,
)

data class ConfigResponse(
    override val oAuthProvider: String?,
    val host: String?,
    val teamsUrl: String?,
    val slackUrl: String?,
) : PublicConfigResponse(oAuthProvider) {
    companion object {
        fun fromConfiguration(configuration: Configuration, oAuthProvider: String?): ConfigResponse {
            return ConfigResponse(
                oAuthProvider = oAuthProvider,
                host = configuration.host,
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
    name = "Controller",
    description = "Configure Kviklet as a whole",
)
class ConfigController(
    val identityProviderProperties: IdentityProviderProperties,
    val configService: ConfigService,
) {

    @GetMapping("/")
    fun getConfig(): PublicConfigResponse {
        try {
            val config = configService.getConfiguration()
            return ConfigResponse.fromConfiguration(config, identityProviderProperties.type?.lowercase())
        } catch (e: Exception) { // FIXME: Use a more specific exception
            return PublicConfigResponse(
                oAuthProvider = identityProviderProperties.type?.lowercase(),
            )
        }
    }

    @PutMapping("/")
    fun createConfig(@RequestBody request: ConfigRequest): ConfigResponse {
        val config = configService.setConfiguration(
            Configuration(
                host = request.host,
                teamsUrl = request.teamsUrl,
                slackUrl = request.slackUrl,
            ),
        )
        return ConfigResponse.fromConfiguration(config, identityProviderProperties.type?.lowercase())
    }
}
