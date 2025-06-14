// This file is not MIT licensed
package dev.kviklet.kviklet.service

import org.slf4j.LoggerFactory
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

class LicenseVerifier {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun loadPublicKey(pem: String): PublicKey {
        val pemContent = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s+".toRegex(), "") // Remove whitespace characters (new lines, spaces, tabs, etc.)

        val keyBytes = Base64.getMimeDecoder().decode(pemContent)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("RSA")
        return kf.generatePublic(spec)
    }

    fun verifyLicense(licenseKey: String, signature: String, publicKey: PublicKey): Boolean {
        try {
            val signatureBytes = Base64.getDecoder().decode(signature)
            val licenseDataJson = licenseKey.toByteArray()

            val sig = Signature.getInstance("RSASSA-PSS")
            val pssParams = PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 20, 1)
            sig.setParameter(pssParams)
            sig.initVerify(publicKey)
            sig.update(licenseDataJson)

            return sig.verify(signatureBytes)
        } catch (e: Exception) {
            logger.error("Error verifying license: ${e.message}", e)
            return false
        }
    }
}
