// This file is not MIT licensed
package dev.kviklet.kviklet.db

import jakarta.persistence.Entity
import jakarta.persistence.Id
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
    val jwk: String,
    val createdAt: LocalDateTime = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime(),
)

interface OAuth2SigningKeyRepository : JpaRepository<OAuth2SigningKeyEntity, String> {
    fun findFirstByOrderByCreatedAtAsc(): OAuth2SigningKeyEntity?
}

/**
 * Stores the key the MCP authorization server signs its access tokens with, as a JWK including the
 * private part. Kept in the database so tokens stay valid across restarts.
 */
@Service
class OAuth2SigningKeyAdapter(private val repository: OAuth2SigningKeyRepository) {

    @Transactional(readOnly = true)
    fun findOldestJwk(): String? = repository.findFirstByOrderByCreatedAtAsc()?.jwk

    @Transactional
    fun create(id: String, jwk: String) {
        repository.save(OAuth2SigningKeyEntity(id = id, jwk = jwk))
    }
}
