package dev.kviklet.kviklet.proxy.postgres

import java.io.ByteArrayInputStream
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
        this.sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, SecureRandom())
    }
}