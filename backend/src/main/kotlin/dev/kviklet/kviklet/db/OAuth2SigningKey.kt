// This file is not MIT licensed
package dev.kviklet.kviklet.db

import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.slf4j.LoggerFactory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@Entity(name = "oauth2_signing_key")
class OAuth2SigningKeyEntity(
    @Id
    val id: String,
    /** The JWK, encrypted like connection credentials while encryption is enabled. */
    var jwk: String,
    val createdAt: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime(),
)

interface OAuth2SigningKeyRepository : JpaRepository<OAuth2SigningKeyEntity, String> {
    fun findFirstByOrderByCreatedAtAsc(): OAuth2SigningKeyEntity?
}

/**
 * Stores the key the MCP authorization server signs its access tokens with, as a JWK including the
 * private part. Kept in the database so tokens stay valid across restarts.
 *
 * Anyone holding the key can sign a token for any user, so it is encrypted like connection
 * credentials while encryption is enabled. Like those, it is stored again when it is read and isn't
 * stored the way the settings ask: encrypted once encryption is turned on, re-encrypted with the
 * current key after a key rotation, and decrypted once encryption is turned off. A key that neither
 * the current nor the previous encryption key decrypts is replaced.
 */
@Service
class OAuth2SigningKeyAdapter(
    private val repository: OAuth2SigningKeyRepository,
    private val encryptionService: EncryptionService,
    private val encryptionConfig: EncryptionConfigProperties,
) {
    private val logger = LoggerFactory.getLogger(OAuth2SigningKeyAdapter::class.java)

    @Transactional
    fun findOldestJwk(): String? {
        val entity = repository.findFirstByOrderByCreatedAtAsc() ?: return null
        val stored = entity.jwk
        val jwk = try {
            if (isPlain(stored)) stored else encryptionService.decrypt(stored)!!
        } catch (e: Exception) {
            // Losing the key only invalidates the current access tokens: clients get new ones with
            // their refresh tokens, which don't depend on it. So a key that can't be decrypted anymore
            // is replaced instead of keeping the MCP server from starting.
            logger.warn("Can't decrypt the MCP signing key, replacing it with a new one", e)
            repository.delete(entity)
            return null
        }
        val storedAsConfigured = if (encryptionConfig.enabled) {
            !isPlain(stored) && !encryptionService.needsReEncryption(stored)
        } else {
            isPlain(stored)
        }
        if (!storedAsConfigured) {
            entity.jwk = encryptionService.encrypt(jwk)
            repository.save(entity)
        }
        return jwk
    }

    @Transactional
    fun create(id: String, jwk: String) {
        repository.save(OAuth2SigningKeyEntity(id = id, jwk = encryptionService.encrypt(jwk)))
    }

    // A JWK is a JSON object, an encrypted one starts with the format prefix (see EncryptionService).
    private fun isPlain(stored: String) = stored.trimStart().startsWith("{")
}
