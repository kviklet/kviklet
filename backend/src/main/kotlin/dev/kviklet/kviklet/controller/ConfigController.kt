package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.LicenseService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class CreateConfigRequest(
    val licenseKey: String,
)

data class ConfigResponse(
    val licenseKey: String,
)

@RestController
@Validated
@RequestMapping("/config")
@Tag(
    name = "Controller",
    description = "Configure Kviklet as a whole",
)
class ConfigController(
    val licenseService: LicenseService,
) {
    @PostMapping("/")
    fun createConfig(
        @Valid @RequestBody
        request: CreateConfigRequest,
    ): ConfigResponse {
        licenseService.createLicense(request.licenseKey)
        return ConfigResponse(
            licenseKey = request.licenseKey,
        )
    }
}
