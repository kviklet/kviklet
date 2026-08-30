package dev.kviklet.kviklet

import dev.kviklet.kviklet.proxy.core.TlsCertEnvConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

// KVI-183: @Value defaults written as "${...:null}" resolve to the literal string "null", not a null
// reference. The null-checks in tlsCertificateFactory then pass and the factory proceeds to
// File("null").readLines() / base64-decode "null" -- which, since the factory runs during eager proxy
// bean creation, crashes the application at boot.
class TLSCertsConfigTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TLSCerts::class.java)

    @Test
    fun `unset TLS certificate properties resolve to real nulls, not the string null`() {
        contextRunner.run { context ->
            val config = context.getBean(TlsCertEnvConfig::class.java)
            assertEquals("NONE", config.PROXY_TLS_CERTIFICATE_SOURCE)
            assertNull(config.PROXY_TLS_CERTIFICATE_FILE)
            assertNull(config.PROXY_TLS_CERTIFICATE_KEY_FILE)
            assertNull(config.PROXY_TLS_CERTIFICATE_KEY)
            assertNull(config.PROXY_TLS_CERTIFICATE_CERT)
        }
    }

    @Test
    fun `set TLS certificate properties are passed through unchanged`() {
        contextRunner.withPropertyValues(
            "proxy.tls_certificate_source=env",
            "proxy.tls_certificate_cert=cert-data",
            "proxy.tls_certificate_key=key-data",
        ).run { context ->
            val config = context.getBean(TlsCertEnvConfig::class.java)
            assertEquals("env", config.PROXY_TLS_CERTIFICATE_SOURCE)
            assertEquals("cert-data", config.PROXY_TLS_CERTIFICATE_CERT)
            assertEquals("key-data", config.PROXY_TLS_CERTIFICATE_KEY)
            assertNull(config.PROXY_TLS_CERTIFICATE_FILE)
            assertNull(config.PROXY_TLS_CERTIFICATE_KEY_FILE)
        }
    }
}
