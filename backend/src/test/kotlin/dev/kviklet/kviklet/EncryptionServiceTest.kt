package dev.kviklet.kviklet

import dev.kviklet.kviklet.db.EncryptionConfigProperties
import dev.kviklet.kviklet.db.EncryptionService
import dev.kviklet.kviklet.helper.LegacyCbcEncryption
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import java.util.Base64

class EncryptionServiceTest {

    companion object {
        private const val KEY_A = "testEncryptionKey123"
        private const val KEY_B = "newEncryptionKey456"

        // Decrypting an AES/CBC block with the wrong key yields a valid PKCS5 padding about once in
        // 256 attempts. Over this many independent ciphertexts the chance that the flaw never shows
        // is (255/256)^3000, well below one in a hundred thousand, so the test is reliably red on
        // unauthenticated encryption and deterministic on authenticated encryption.
        private const val ATTEMPTS = 3000
    }

    private val config = EncryptionConfigProperties().apply { enabled = true }
    private val service = EncryptionService(config)

    private fun keys(current: String, previous: String? = null) {
        config.key = EncryptionConfigProperties.KeyProperties(current = current, previous = previous)
    }

    @Test
    fun `encrypts and decrypts with the current key`() {
        keys(KEY_A)
        val encrypted = service.encrypt("s3cret pässword")
        encrypted shouldNotBe "s3cret pässword"
        service.decrypt(encrypted) shouldBe "s3cret pässword"
    }

    @Test
    fun `returns the value unchanged when encryption is disabled`() {
        config.enabled = false
        keys(KEY_A)
        service.encrypt("plain") shouldBe "plain"
    }

    @Test
    fun `decrypting with the wrong key never returns a wrong plaintext`() {
        keys(KEY_A)
        val ciphertexts = List(ATTEMPTS) { service.encrypt("rotationuser") }

        keys(KEY_B)
        ciphertexts.forEach { ciphertext ->
            shouldThrowAny { service.decrypt(ciphertext) }
        }
    }

    @Test
    fun `values encrypted with the previous key are still decrypted during rotation`() {
        keys(KEY_A)
        val ciphertexts = List(ATTEMPTS) { service.encrypt("rotationuser") }

        keys(current = KEY_B, previous = KEY_A)
        ciphertexts.forEach { ciphertext ->
            service.decrypt(ciphertext) shouldBe "rotationuser"
        }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        keys(KEY_A)
        val encrypted = service.encrypt("rotationuser")
        val payloadStart = encrypted.lastIndexOf(':') + 1
        val payload = Base64.getDecoder().decode(encrypted.substring(payloadStart))
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0x01).toByte()
        val tampered = encrypted.substring(0, payloadStart) + Base64.getEncoder().encodeToString(payload)

        shouldThrowAny { service.decrypt(tampered) }
    }

    @Test
    fun `ciphertext encrypted with a key that is no longer configured fails with a clear error`() {
        keys(KEY_A)
        val encrypted = service.encrypt("rotationuser")

        keys(KEY_B)
        val error = shouldThrowAny { service.decrypt(encrypted) }
        error.message shouldNotBe null
        error.message!! shouldStartWith "No configured encryption key matches"
    }

    @Test
    fun `legacy CBC ciphertexts still decrypt`() {
        keys(KEY_A)
        service.decrypt(LegacyCbcEncryption.encrypt("legacyuser", KEY_A)) shouldBe "legacyuser"
    }

    @Test
    fun `legacy CBC ciphertexts under the previous key never decrypt to garbage during rotation`() {
        val ciphertexts = List(ATTEMPTS) { LegacyCbcEncryption.encrypt("rotationuser", KEY_A) }

        keys(current = KEY_B, previous = KEY_A)
        ciphertexts.forEach { ciphertext ->
            service.decrypt(ciphertext) shouldBe "rotationuser"
        }
    }

    @Test
    fun `legacy CBC ciphertexts under the current key still decrypt while a previous key is configured`() {
        // Long enough that a wrong-key decryption cannot pass for valid text by accident
        val plaintext = "a-rather-long-password-with-more-than-three-blocks"
        val ciphertexts = List(ATTEMPTS) { LegacyCbcEncryption.encrypt(plaintext, KEY_A) }

        keys(current = KEY_A, previous = KEY_B)
        ciphertexts.forEach { ciphertext ->
            service.decrypt(ciphertext) shouldBe plaintext
        }
    }

    @Test
    fun `new ciphertexts are distinguishable from legacy ones`() {
        keys(KEY_A)
        LegacyCbcEncryption.encrypt("legacyuser", KEY_A) shouldNotContain ":"
        service.encrypt("newuser") shouldStartWith "aes-gcm:"
    }

    @Test
    fun `reports which stored values need to be re-encrypted`() {
        keys(KEY_A)
        val legacy = LegacyCbcEncryption.encrypt("user", KEY_A)
        val underA = service.encrypt("user")
        keys(current = KEY_B, previous = KEY_A)
        val underB = service.encrypt("user")

        service.needsReEncryption(legacy).shouldBeTrue()
        service.needsReEncryption(underA).shouldBeTrue()
        service.needsReEncryption(underB).shouldBeFalse()
    }
}
