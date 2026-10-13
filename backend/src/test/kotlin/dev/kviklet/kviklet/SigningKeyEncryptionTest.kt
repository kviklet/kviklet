package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.EncryptionConfigProperties
import dev.kviklet.kviklet.db.OAuth2SigningKeyAdapter
import dev.kviklet.kviklet.db.OAuth2SigningKeyEntity
import dev.kviklet.kviklet.db.OAuth2SigningKeyRepository
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime

@SpringBootTest
@ActiveProfiles("test")
class SigningKeyEncryptionTest(
    @Autowired val adapter: OAuth2SigningKeyAdapter,
    @Autowired val repository: OAuth2SigningKeyRepository,
    @Autowired val encryptionConfig: EncryptionConfigProperties,
) {
    companion object {
        private const val OLD_KEY = "oldEncryptionKey123"
        private const val NEW_KEY = "newEncryptionKey456"
        private const val ID = "test-signing-key"
        private const val JWK = """{"kty":"RSA","kid":"test-signing-key","n":"abc","e":"AQAB","d":"secret"}"""
    }

    private var originalEnabled = false
    private var originalKey: EncryptionConfigProperties.KeyProperties? = null

    @BeforeEach
    fun setUp() {
        originalEnabled = encryptionConfig.enabled
        originalKey = encryptionConfig.key
        useKeys(current = OLD_KEY)
    }

    @AfterEach
    fun tearDown() {
        repository.deleteById(ID)
        encryptionConfig.enabled = originalEnabled
        encryptionConfig.key = originalKey
    }

    @Test
    fun `a new key is stored encrypted`() {
        adapter.create(ID, JWK)

        stored() shouldStartWith "aes-gcm:"
    }

    @Test
    fun `a key stored before encryption was turned on is encrypted when it is read`() {
        seed(JWK)

        adapter.findOldestJwk() shouldBe JWK
        stored() shouldStartWith "aes-gcm:"
        adapter.findOldestJwk() shouldBe JWK
    }

    @Test
    fun `after an encryption key rotation the key is re-encrypted with the new key`() {
        adapter.create(ID, JWK)
        backdate()
        val encryptedWithOldKey = stored()

        useKeys(current = NEW_KEY, previous = OLD_KEY)
        adapter.findOldestJwk() shouldBe JWK
        (stored() == encryptedWithOldKey) shouldBe false

        // Readable with the new key alone, so the old one can be dropped from the configuration.
        useKeys(current = NEW_KEY)
        adapter.findOldestJwk() shouldBe JWK
    }

    @Test
    fun `turning encryption off stores the key in plain text again`() {
        adapter.create(ID, JWK)
        backdate()

        encryptionConfig.enabled = false
        adapter.findOldestJwk() shouldBe JWK
        stored() shouldBe JWK
    }

    @Test
    fun `a key that can't be decrypted anymore is replaced`() {
        adapter.create(ID, JWK)
        backdate()

        useKeys(current = NEW_KEY)
        // No key is returned, so a new one is generated and stored.
        adapter.findOldestJwk().shouldBeNull()
        repository.findById(ID).isPresent shouldBe false
    }

    private fun useKeys(current: String, previous: String? = null) {
        encryptionConfig.enabled = true
        encryptionConfig.key = EncryptionConfigProperties.KeyProperties(current = current, previous = previous)
    }

    // Older than the key the application created at startup, so it is the one the adapter reads.
    private fun seed(jwk: String) {
        repository.save(OAuth2SigningKeyEntity(id = ID, jwk = jwk, createdAt = LocalDateTime.of(2000, 1, 1, 0, 0)))
    }

    private fun backdate() = seed(stored())

    private fun stored() = repository.findById(ID).get().jwk
}
