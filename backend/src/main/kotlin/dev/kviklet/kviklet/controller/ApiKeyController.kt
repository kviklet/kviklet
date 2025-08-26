// This file is not MIT licensed
package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.security.CurrentUser
import dev.kviklet.kviklet.security.EnterpriseOnly
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.ApiKeyService
import dev.kviklet.kviklet.service.dto.ApiKeyId
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

data class ListApiKeysResponse(val apiKeys: List<ApiKeyResponse>)

data class ApiKeyResponse(
    val id: String,
    val name: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime?,
    val lastUsedAt: LocalDateTime?,
)

data class ApiKeyWithSecretResponse(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: LocalDateTime,
    val expiresAt: LocalDateTime?,
)

data class CreateApiKeyRequest(val name: String, val expiresInDays: Int?)

@RestController
@Validated
@RequestMapping("/api-keys")
@Tag(
    name = "Api Keys",
)
class ApiKeyController(private val apiKeyService: ApiKeyService) {
    @GetMapping("/")
    @EnterpriseOnly(
        "Api Keys",
    )
    fun listApiKeys(@CurrentUser user: UserDetailsWithId): ListApiKeysResponse {
        val apiKeys = apiKeyService.listApiKeys()

        return ListApiKeysResponse(
            apiKeys = apiKeys.map { apiKey ->
                ApiKeyResponse(
                    id = apiKey.id?.toString() ?: throw IllegalStateException("API Key ID should not be null"),
                    name = apiKey.name,
                    createdAt = apiKey.createdAt,
                    expiresAt = apiKey.expiresAt,
                    lastUsedAt = apiKey.lastUsedAt,
                )
            },
        )
    }

    @GetMapping("/{id}")
    @EnterpriseOnly(
        "Api Keys",
    )
    fun getApiKey(@PathVariable id: ApiKeyId, @CurrentUser user: UserDetailsWithId): ApiKeyResponse {
        val apiKey = apiKeyService.getApiKey(id)

        return ApiKeyResponse(
            id = apiKey.id?.toString() ?: throw IllegalStateException("API Key ID should not be null"),
            name = apiKey.name,
            createdAt = apiKey.createdAt,
            expiresAt = apiKey.expiresAt,
            lastUsedAt = apiKey.lastUsedAt,
        )
    }

    @PostMapping("/")
    @EnterpriseOnly(
        "Api Keys",
    )
    fun createApiKey(
        @CurrentUser user: UserDetailsWithId,
        @RequestBody request: CreateApiKeyRequest,
    ): ResponseEntity<ApiKeyWithSecretResponse> {
        val result = apiKeyService.createApiKey(
            userId = user.id,
            name = request.name,
            expiresInDays = request.expiresInDays,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(
            ApiKeyWithSecretResponse(
                id = result.id.toString(),
                name = result.name,
                key = result.key!!,
                createdAt = result.createdAt,
                expiresAt = result.expiresAt,
            ),
        )
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @EnterpriseOnly(
        "Api Keys",
    )
    fun deleteApiKey(@PathVariable id: ApiKeyId) {
        apiKeyService.deleteApiKey(id)
    }
}
