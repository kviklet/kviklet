package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.db.ApiKeyId
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.CurrentUser
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.ApiKeyService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

data class ListApiKeysResponse(val apiKeys: List<ApiKeyResponse>)

data class ApiKeyResponse(
    val id: String,
    val name: String,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime?,
    val lastUsedAt: ZonedDateTime?,
)

data class ApiKeyWithSecretResponse(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime?,
)

data class CreateApiKeyRequest(val name: String, val expiresInDays: Int?)

@RestController
@RequestMapping("/api-key")
class ApiKeyController(private val apiKeyService: ApiKeyService) {
    @GetMapping
    fun listApiKeys(@CurrentUser user: UserDetailsWithId): ResponseEntity<ListApiKeysResponse> {
        val apiKeys = apiKeyService.listApiKeysForUser(UserId(user.id))

        return ResponseEntity.ok(
            ListApiKeysResponse(
                apiKeys = apiKeys.map { apiKey ->
                    ApiKeyResponse(
                        id = apiKey.id?.toString() ?: throw IllegalStateException("API Key ID should not be null"),
                        name = apiKey.name,
                        createdAt = apiKey.createdAt,
                        expiresAt = apiKey.expiresAt,
                        lastUsedAt = apiKey.lastUsedAt,
                    )
                },
            ),
        )
    }

    @GetMapping("/{id}")
    fun getApiKey(@PathVariable id: ApiKeyId, @CurrentUser user: UserDetailsWithId): ResponseEntity<ApiKeyResponse> {
        val apiKey = apiKeyService.getApiKey(id)

        if (apiKey.userId.toString() != user.id) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        return ResponseEntity.ok(
            ApiKeyResponse(
                id = apiKey.id?.toString() ?: throw IllegalStateException("API Key ID should not be null"),
                name = apiKey.name,
                createdAt = apiKey.createdAt,
                expiresAt = apiKey.expiresAt,
                lastUsedAt = apiKey.lastUsedAt,
            ),
        )
    }

    @PostMapping
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
                id = result.id,
                name = result.name,
                key = result.key,
                createdAt = result.createdAt,
                expiresAt = result.expiresAt,
            ),
        )
    }

    @DeleteMapping("/{id}")
    fun deleteApiKey(@PathVariable id: ApiKeyId): ResponseEntity<Void> {
        apiKeyService.deleteApiKey(id)
        return ResponseEntity.noContent().build()
    }
}
