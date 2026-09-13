package dev.kviklet.kviklet.helper

import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Produces ciphertexts in the format Kviklet stored before it switched to authenticated encryption:
 * base64(iv || AES/CBC/PKCS5Padding(SHA-256(key), value)) with no marker of any kind. Deployments
 * that enabled encryption before that switch still have values like these in the connection table,
 * so the tests use this to seed them.
 */
object LegacyCbcEncryption {
    fun encrypt(value: String, key: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val iv = ByteArray(cipher.blockSize).also { java.security.SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sha256(key), "AES"), IvParameterSpec(iv))
        return Base64.getEncoder().encodeToString(iv + cipher.doFinal(value.toByteArray()))
    }

    private fun sha256(key: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
}
