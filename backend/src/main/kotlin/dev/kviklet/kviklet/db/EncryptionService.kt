package dev.kviklet.kviklet.db
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Configuration
@ConfigurationProperties(prefix = "encryption")
class EncryptionConfigProperties {
    var enabled: Boolean = false
    var key: KeyProperties? = null
    class KeyProperties(val current: String, val previous: String?)
}

/**
 * Encrypts connection credentials at rest.
 *
 * Values are stored as `aes-gcm:<key id>:<base64(iv || ciphertext || tag)>`. The GCM tag makes a
 * decryption with the wrong key fail deterministically, and the key id selects the configured key
 * (current or previous) up front instead of guessing. Values without the prefix are legacy
 * `base64(iv || AES/CBC/PKCS5Padding)` ciphertexts from before this format existed; they are still
 * readable and get rewritten in the new format the next time the connection is saved.
 */
@Service
class EncryptionService(private val config: EncryptionConfigProperties) {
    companion object {
        private const val FORMAT_PREFIX = "aes-gcm"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val LEGACY_CBC_IV_BYTES = 16
    }

    private class DerivedKey(val id: String, val spec: SecretKeySpec)

    fun loadKey(): String = config.key?.current ?: throw IllegalStateException("No encryption key found")

    fun encrypt(value: String): String {
        if (!config.enabled) return value

        val key = deriveKey(loadKey())
        val iv = ByteArray(GCM_IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key.spec, GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        return "$FORMAT_PREFIX:${key.id}:" + Base64.getEncoder().encodeToString(iv + encrypted)
    }

    fun decrypt(encrypted: String?): String? {
        if (encrypted == null) return null
        return if (isLegacyFormat(encrypted)) decryptLegacy(encrypted) else decryptGcm(encrypted)
    }

    /**
     * True if the stored value is not encrypted with the current key in the current format, i.e. it
     * should be rewritten so that a previous key can eventually be dropped.
     */
    fun needsReEncryption(encrypted: String): Boolean {
        if (isLegacyFormat(encrypted)) return true
        return parse(encrypted).keyId != deriveKey(loadKey()).id
    }

    private class Parsed(val keyId: String, val payload: ByteArray)

    private fun isLegacyFormat(encrypted: String): Boolean = !encrypted.startsWith("$FORMAT_PREFIX:")

    private fun parse(encrypted: String): Parsed {
        val parts = encrypted.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_PREFIX) { "Unrecognized encrypted value format" }
        return Parsed(keyId = parts[1], payload = Base64.getDecoder().decode(parts[2]))
    }

    private fun decryptGcm(encrypted: String): String {
        val parsed = parse(encrypted)
        val key = configuredKeys().firstOrNull { it.id == parsed.keyId }
            ?: throw IllegalStateException(
                "No configured encryption key matches key id ${parsed.keyId}. " +
                    "Set the key the value was encrypted with as ENCRYPTION_KEY_PREVIOUS to rotate it.",
            )
        val iv = parsed.payload.copyOfRange(0, GCM_IV_BYTES)
        val ciphertext = parsed.payload.copyOfRange(GCM_IV_BYTES, parsed.payload.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key.spec, GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
    }

    /**
     * Legacy CBC values carry no key id and no integrity check, so the key has to be found by trial.
     * A wrong key yields a valid PKCS5 padding about once in 256 attempts, which is why the result is
     * additionally required to be valid UTF-8 before it is accepted. The previous key is tried first
     * because when an upgrade and a rotation happen together every legacy value was written with it,
     * so the wrong-key trial is skipped entirely in the common case.
     */
    private fun decryptLegacy(encrypted: String): String {
        val decoded = Base64.getDecoder().decode(encrypted)
        val iv = IvParameterSpec(decoded.copyOfRange(0, LEGACY_CBC_IV_BYTES))
        val ciphertext = decoded.copyOfRange(LEGACY_CBC_IV_BYTES, decoded.size)

        var lastError: Exception? = null
        for (key in configuredKeys().reversed()) {
            try {
                val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(Cipher.DECRYPT_MODE, key.spec, iv)
                return decodeStrictUtf8(cipher.doFinal(ciphertext))
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No encryption key found")
    }

    private fun decodeStrictUtf8(bytes: ByteArray): String = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        throw IllegalStateException("Decrypted value is not valid text, the key is probably wrong", e)
    }

    /** Current key first, then the previous one if configured. */
    private fun configuredKeys(): List<DerivedKey> {
        val keys = config.key ?: throw IllegalStateException("No encryption key found")
        return listOfNotNull(keys.current, keys.previous?.takeIf { it.isNotBlank() }).map { deriveKey(it) }
    }

    private fun deriveKey(password: String): DerivedKey {
        val keyBytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(StandardCharsets.UTF_8))
        val keyId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(keyBytes), 0, 4)
        return DerivedKey(id = keyId, spec = SecretKeySpec(keyBytes, "AES"))
    }
}
