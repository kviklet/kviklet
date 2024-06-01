package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.IdentityProviderProperties
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateConfigRequest(
    val licenseKey: String,
)

data class ConfigResponse(
    val oAuthProvider: String?,
)

@RestController
@Validated
@RequestMapping("/config")
@Tag(
    name = "Controller",
    description = "Configure Kviklet as a whole",
)
class ConfigController(
    val IdentityProviderProperties: IdentityProviderProperties,
) {

    @GetMapping("/")
    fun getConfig(): ConfigResponse {
        return ConfigResponse(
            oAuthProvider = IdentityProviderProperties.type?.lowercase(),
        )
    }
}
