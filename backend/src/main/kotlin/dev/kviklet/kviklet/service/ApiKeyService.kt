package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ApiKey
import dev.kviklet.kviklet.db.ApiKeyAdapter
import dev.kviklet.kviklet.db.ApiKeyId
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.*

private const val KEY_LENGTH = 32

data class ApiKeyCreationResult(
    val id: String,
    val name: String,
    val key: String,
    val createdAt: ZonedDateTime,
    val expiresAt: ZonedDateTime?,
)

@Service
class ApiKeyService(
    private val apiKeyAdapter: ApiKeyAdapter,
    private val userAdapter: UserAdapter,
    private val passwordEncoder: PasswordEncoder,
) {
    @Transactional
    @Policy(Permission.API_KEY_CREATE)
    fun createApiKey(userId: String, name: String, expiresInDays: Int? = null): ApiKeyCreationResult {
        try {
            userAdapter.findById(userId)
        } catch (e: EntityNotFound) {
            throw EntityNotFound("User with ID $userId not found", e.message)
        }

        val random = SecureRandom()
        val bytes = ByteArray(KEY_LENGTH)
        random.nextBytes(bytes)

        val keyValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        val keyHash = passwordEncoder.encode(keyValue)

        // TODO consider if this is correct. Should API keys be zone independent or not?
        val now = utcTimeNow().atZone(ZoneOffset.UTC)
        val expiresAt = expiresInDays?.let { now.plusDays(it.toLong()) }

        val apiKeyEntity = ApiKey(
            name = name,
            createdAt = now,
            expiresAt = expiresAt,
            userId = UserId(userId),
            keyHash = keyHash,
        )
        val savedApiKey = apiKeyAdapter.create(apiKeyEntity)

        return ApiKeyCreationResult(
            id = savedApiKey.id?.toString() ?: throw EntityNotFound(
                "API key ID not found",
                "API key $savedApiKey has no ID",
            ),
            name = savedApiKey.name,
            key = keyValue,
            createdAt = savedApiKey.createdAt,
            expiresAt = savedApiKey.expiresAt,
        )
    }

    @Transactional(readOnly = true)
    @Policy(Permission.API_KEY_GET)
    fun listApiKeysForUser(userId: UserId): List<ApiKey> = apiKeyAdapter.findAllByUserId(userId.toString())

    @Transactional
    @Policy(Permission.API_KEY_DELETE)
    fun deleteApiKey(id: ApiKeyId) {
        apiKeyAdapter.deleteApiKey(id)
    }

    @Transactional(readOnly = true)
    @Policy(Permission.API_KEY_GET)
    fun getApiKey(id: ApiKeyId) = apiKeyAdapter.findById(id)

    @Transactional
    @Policy(Permission.API_KEY_GET)
    fun updateLastUsed(id: String) {
        apiKeyAdapter.updateLastUsed(id, utcTimeNow().atZone(ZoneOffset.UTC))
    }
}
