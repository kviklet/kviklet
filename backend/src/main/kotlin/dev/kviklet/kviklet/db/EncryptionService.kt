package dev.kviklet.kviklet.db
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Configuration
@ConfigurationProperties(prefix = "encryption")
class EncryptionConfigProperties {
    var enabled: Boolean = false
    var key: KeyProperties? = null
    class KeyProperties(val current: String, val previous: String?) {
        fun bothKeysProvided(): Boolean = current.isNotBlank() && !previous.isNullOrBlank()
    }
}

@Service
class EncryptionService(private val config: EncryptionConfigProperties) {
    private val algorithm = "AES/CBC/PKCS5Padding"

    fun loadKey(): String = config.key?.current ?: throw IllegalStateException("No encryption key found")

    fun encrypt(value: String): String {
        if (!config.enabled) return value

        val key = getKeyFromPassword(loadKey())
        val cipher = Cipher.getInstance(algorithm)
        val iv = generateIv(cipher)
        cipher.init(Cipher.ENCRYPT_MODE, key, iv)
        val encrypted = cipher.doFinal(value.toByteArray())
        return Base64.getEncoder().encodeToString(iv.iv + encrypted)
    }

    fun decrypt(encrypted: String?): String? {
        if (encrypted == null) return null

        val decoded = Base64.getDecoder().decode(encrypted)
        val iv = IvParameterSpec(decoded.copyOfRange(0, 16))
        val encryptedBytes = decoded.copyOfRange(16, decoded.size)

        return try {
            decryptWithKey(loadKey(), iv, encryptedBytes)
        } catch (e: Exception) {
            if (!config.key?.previous.isNullOrBlank()) {
                decryptWithKey(config.key!!.previous!!, iv, encryptedBytes)
            } else {
                throw e
            }
        }
    }

    private fun decryptWithKey(key: String, iv: IvParameterSpec, encryptedBytes: ByteArray): String {
        val secretKey = getKeyFromPassword(key)
        val cipher = Cipher.getInstance(algorithm)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        return String(cipher.doFinal(encryptedBytes))
    }

    private fun getKeyFromPassword(password: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = password.toByteArray()
        digest.update(bytes, 0, bytes.size)
        val key = digest.digest()
        return SecretKeySpec(key, "AES")
    }

    private fun generateIv(cipher: Cipher): IvParameterSpec {
        val iv = ByteArray(cipher.blockSize)
        val secureRandom = java.security.SecureRandom()
        secureRandom.nextBytes(iv)
        return IvParameterSpec(iv)
    }
}
