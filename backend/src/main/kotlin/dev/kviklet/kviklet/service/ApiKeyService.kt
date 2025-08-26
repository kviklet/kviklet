package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.ApiKeyAdapter
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.db.UserId
import dev.kviklet.kviklet.security.NoPolicy
import dev.kviklet.kviklet.security.Permission
import dev.kviklet.kviklet.security.Policy
import dev.kviklet.kviklet.service.dto.ApiKey
import dev.kviklet.kviklet.service.dto.ApiKeyId
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

private const val KEY_LENGTH = 32

@Service
class ApiKeyService(
    private val apiKeyAdapter: ApiKeyAdapter,
    private val userAdapter: UserAdapter,
    private val passwordEncoder: PasswordEncoder,
) {
    
    private fun hashApiKey(apiKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(apiKey.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
    @Transactional
    @Policy(Permission.API_KEY_CREATE)
    fun createApiKey(userId: String, name: String, expiresInDays: Int? = null): ApiKey {
        try {
            userAdapter.findById(userId)
        } catch (e: EntityNotFound) {
            throw EntityNotFound("User with ID $userId not found", e.message)
        }

        val random = SecureRandom()
        val bytes = ByteArray(KEY_LENGTH)
        random.nextBytes(bytes)

        val keyValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        val keyHash = hashApiKey(keyValue)

        val now = utcTimeNow()
        val expiresAt = expiresInDays?.let { now.plusDays(it.toLong()) }

        val apiKey = ApiKey(
            name = name,
            createdAt = now,
            expiresAt = expiresAt,
            userId = UserId(userId),
            keyHash = keyHash,
            key = keyValue,
        )
        val savedApiKey = apiKeyAdapter.create(apiKey)
        return savedApiKey.copy(key = keyValue) // ensuring we return the keyvalue on create
    }

    @Transactional(readOnly = true)
    @Policy(Permission.API_KEY_GET)
    fun listApiKeys(): List<ApiKey> = apiKeyAdapter.findAll()

    @Transactional
    @Policy(Permission.API_KEY_EDIT)
    fun deleteApiKey(id: ApiKeyId) {
        apiKeyAdapter.deleteApiKey(id)
    }

    @Transactional(readOnly = true)
    @Policy(Permission.API_KEY_GET)
    fun getApiKey(id: ApiKeyId) = apiKeyAdapter.findById(id)

    @Transactional
    @Policy(Permission.API_KEY_GET)
    fun updateLastUsed(id: String) {
        apiKeyAdapter.updateApiKey(id, utcTimeNow())
    }

    @Transactional(readOnly = true)
    @NoPolicy
    fun checkKey(apiKey: String): ApiKey? {
        val keyHash = hashApiKey(apiKey)
        val key = apiKeyAdapter.findByHash(keyHash)
        val now = utcTimeNow()
        if (key?.expiresAt?.isBefore(now) == true) {
            return null
        }
        return key
    }
}
