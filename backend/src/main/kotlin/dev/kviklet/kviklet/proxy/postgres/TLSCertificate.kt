package dev.kviklet.kviklet.proxy.postgres

import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.File
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class TLSCertificate(privKey: String, cert: String) {
    var sslContext: SSLContext

    init {

        val certFactory = CertificateFactory.getInstance("X.509")
        var certificate: Certificate
        ByteArrayInputStream(
            Base64.getDecoder().decode(cert)
        ).use { certStream ->
            certificate = certFactory.generateCertificate(certStream)
        }
        val keyFactory = KeyFactory.getInstance("RSA")
        val privateKey: PrivateKey
        ByteArrayInputStream(
            Base64.getDecoder().decode(privKey)
        ).use { keyStream ->
            val keySpec = PKCS8EncodedKeySpec(keyStream.readAllBytes())
            privateKey = keyFactory.generatePrivate(keySpec)
        }
        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry("server", privateKey, CharArray(0), arrayOf(certificate))
        val kmf = KeyManagerFactory.getInstance("SunX509")
        kmf.init(keyStore, CharArray(0))
        sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
    }
}

fun preprocessPEMObject(pem: String): String {
    return pem
        .replace("\\s".toRegex(), "")
        .replace("-----BEGINRSAPRIVATEKEY-----", "")
        .replace("-----ENDRSAPRIVATEKEY-----", "")
        .replace("-----BEGINCERTIFICATE-----", "")
        .replace("-----ENDCERTIFICATE-----", "")
}

// The only reason for existence of this class is to mock env variables
class TlsCertEnvConfig {
    var PROXY_TLS_CERTIFICATES_SOURCE: String = System.getenv("PROXY_TLS_CERTIFICATES_SOURCE") ?: "NONE"
    var PROXY_TLS_CERTIFICATE_FILE: String? = System.getenv("PROXY_TLS_CERTIFICATE_FILE")
    var PROXY_TLS_CERTIFICATE_KEY_FILE: String? = System.getenv("PROXY_TLS_CERTIFICATE_KEY_FILE")
    var PROXY_TLS_CERTIFICATE_KEY: String? = System.getenv("PROXY_TLS_CERTIFICATE_KEY")
    var PROXY_TLS_CERTIFICATE_CERT: String? = System.getenv("PROXY_TLS_CERTIFICATE_CERT")
}

fun tlsCertificateFactory(env: TlsCertEnvConfig = TlsCertEnvConfig()): TLSCertificate? {
    val logger = LoggerFactory.getLogger("TLSCertificate")

    when (env.PROXY_TLS_CERTIFICATES_SOURCE.lowercase()) {
        "file" -> {
            if (env.PROXY_TLS_CERTIFICATE_FILE == null || env.PROXY_TLS_CERTIFICATE_KEY_FILE == null) {
                logger.error("PROXY_TLS_CERTIFICATES_SOURCE is set to file but PROXY_TLS_CERTIFICATE_FILE or PROXY_TLS_CERTIFICATE_KEY_FILE are not set")
                return null
            }
            return TLSCertificate(
                preprocessPEMObject(
                    File(env.PROXY_TLS_CERTIFICATE_KEY_FILE!!).readLines().joinToString(separator = "\n")
                ),
                preprocessPEMObject(
                    File(env.PROXY_TLS_CERTIFICATE_FILE!!).readLines().joinToString(separator = "\n")
                )
            )
        }

        "env" -> {
            if (env.PROXY_TLS_CERTIFICATE_CERT == null || env.PROXY_TLS_CERTIFICATE_KEY == null) {
                logger.error("PROXY_TLS_CERTIFICATES_SOURCE is set to file but PROXY_TLS_CERTIFICATE_CERT or PROXY_TLS_CERTIFICATE_KEY are not set")
                return null
            }
            return TLSCertificate(
                preprocessPEMObject(env.PROXY_TLS_CERTIFICATE_KEY!!),
                preprocessPEMObject(env.PROXY_TLS_CERTIFICATE_CERT!!)
            )
        }

        else -> {
            logger.warn("PROXY_TLS_CERTIFICATES_SOURCE was not found, the proxy won't support TLS ")
            return null
        }
    }
}