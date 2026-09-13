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
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionServiceTest {

    companion object {
        private const val KEY_A = "testEncryptionKey123"
        private const val KEY_B = "newEncryptionKey456"
    }

    private val config = EncryptionConfigProperties().apply { enabled = true }
    private val service = EncryptionService(config)

    private fun keys(current: String, previous: String? = null) {
        config.key = EncryptionConfigProperties.KeyProperties(current = current, previous = previous)
    }

    private fun keyIdOf(encrypted: String): String = encrypted.split(':')[1]

    private fun payloadOf(encrypted: String): ByteArray = Base64.getDecoder().decode(encrypted.split(':')[2])

    private fun gcmValue(keyId: String, payload: ByteArray): String =
        "aes-gcm:$keyId:" + Base64.getEncoder().encodeToString(payload)

    /** A legacy CBC value whose (unpadded) plaintext block is exactly [block], for crafting bad padding. */
    private fun legacyValueWithRawBlock(block: ByteArray, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/NoPadding")
        val iv = ByteArray(16) { it.toByte() }
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), IvParameterSpec(iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(block))
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
    fun `values encrypted with the previous key are still decrypted during rotation`() {
        keys(KEY_A)
        val encrypted = service.encrypt("rotationuser")

        keys(current = KEY_B, previous = KEY_A)
        service.decrypt(encrypted) shouldBe "rotationuser"
    }

    @Test
    fun `ciphertext encrypted with a key that is no longer configured fails with a clear error`() {
        keys(KEY_A)
        val encrypted = service.encrypt("rotationuser")

        keys(KEY_B)
        val error = shouldThrowAny { service.decrypt(encrypted) }
        error.message!! shouldStartWith "No configured encryption key matches"
    }

    @Test
    fun `a wrong key is rejected even when the key id claims otherwise`() {
        keys(KEY_A)
        val encryptedUnderA = service.encrypt("rotationuser")
        keys(KEY_B)
        val forged = gcmValue(keyIdOf(service.encrypt("anything")), payloadOf(encryptedUnderA))

        shouldThrowAny { service.decrypt(forged) }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        keys(KEY_A)
        val encrypted = service.encrypt("rotationuser")
        val payload = payloadOf(encrypted)
        payload[payload.size - 1] = (payload[payload.size - 1].toInt() xor 0x01).toByte()

        shouldThrowAny { service.decrypt(gcmValue(keyIdOf(encrypted), payload)) }
    }

    @Test
    fun `legacy CBC ciphertexts decrypt with a single configured key`() {
        keys(KEY_A)
        service.decrypt(LegacyCbcEncryption.encrypt("legacyuser", KEY_A)) shouldBe "legacyuser"
    }

    @Test
    fun `legacy CBC ciphertexts are refused while a previous key is configured`() {
        val underPrevious = LegacyCbcEncryption.encrypt("rotationuser", KEY_A)
        val underCurrent = LegacyCbcEncryption.encrypt("rotationuser", KEY_B)

        keys(current = KEY_B, previous = KEY_A)
        listOf(underPrevious, underCurrent).forEach { ciphertext ->
            val error = shouldThrowAny { service.decrypt(ciphertext) }
            error.message!! shouldStartWith "Found a credential encrypted by an older Kviklet version"
        }
    }

    @Test
    fun `legacy CBC ciphertext with invalid padding is rejected`() {
        keys(KEY_A)
        // 0x11 exceeds the block size, so it can never be a valid PKCS5 padding byte
        val block = ByteArray(16) { 'a'.code.toByte() }.also { it[15] = 0x11 }

        shouldThrowAny { service.decrypt(legacyValueWithRawBlock(block, KEY_A)) }
    }

    @Test
    fun `legacy CBC ciphertext with valid padding but invalid text is rejected`() {
        keys(KEY_A)
        // Valid one-byte padding, but 0xFF can never appear in UTF-8
        val block = ByteArray(16) { 'a'.code.toByte() }.also {
            it[14] = 0xFF.toByte()
            it[15] = 0x01
        }

        val error = shouldThrowAny { service.decrypt(legacyValueWithRawBlock(block, KEY_A)) }
        error.message!! shouldStartWith "Decrypted value is not valid text"
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
