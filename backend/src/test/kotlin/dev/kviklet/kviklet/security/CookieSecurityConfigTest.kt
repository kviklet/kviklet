package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.security.saml.SamlProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.session.web.http.DefaultCookieSerializer

class CookieSecurityConfigTest {

    @Test
    fun `cookie config with SAML disabled uses Lax and insecure`() {
        val samlProperties = SamlProperties().apply {
            enabled = false
        }

        val config = CookieSecurityConfig(samlProperties)
        val serializer = config.cookieSerializer() as DefaultCookieSerializer

        // Access private fields through reflection for testing
        val sameSiteField = DefaultCookieSerializer::class.java.getDeclaredField("sameSite")
        sameSiteField.isAccessible = true
        val sameSite = sameSiteField.get(serializer) as String?

        val useSecureCookieField = DefaultCookieSerializer::class.java.getDeclaredField("useSecureCookie")
        useSecureCookieField.isAccessible = true
        val useSecureCookie = useSecureCookieField.get(serializer) as Boolean?

        assertEquals("Lax", sameSite)
        assertEquals(false, useSecureCookie)
    }

    @Test
    fun `cookie config with SAML enabled but incomplete config uses Lax and insecure`() {
        val samlProperties = SamlProperties().apply {
            enabled = true
            entityId = null // Missing required field
        }

        val config = CookieSecurityConfig(samlProperties)
        val serializer = config.cookieSerializer() as DefaultCookieSerializer

        val sameSiteField = DefaultCookieSerializer::class.java.getDeclaredField("sameSite")
        sameSiteField.isAccessible = true
        val sameSite = sameSiteField.get(serializer) as String?

        val useSecureCookieField = DefaultCookieSerializer::class.java.getDeclaredField("useSecureCookie")
        useSecureCookieField.isAccessible = true
        val useSecureCookie = useSecureCookieField.get(serializer) as Boolean?

        assertEquals("Lax", sameSite)
        assertEquals(false, useSecureCookie)
    }

    @Test
    fun `cookie config with SAML fully enabled uses None and secure`() {
        val samlProperties = SamlProperties().apply {
            enabled = true
            entityId = "https://test.com"
            ssoServiceLocation = "https://test.com/sso"
            verificationCertificate = "-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----"
        }

        val config = CookieSecurityConfig(samlProperties)
        val serializer = config.cookieSerializer() as DefaultCookieSerializer

        val sameSiteField = DefaultCookieSerializer::class.java.getDeclaredField("sameSite")
        sameSiteField.isAccessible = true
        val sameSite = sameSiteField.get(serializer) as String?

        val useSecureCookieField = DefaultCookieSerializer::class.java.getDeclaredField("useSecureCookie")
        useSecureCookieField.isAccessible = true
        val useSecureCookie = useSecureCookieField.get(serializer) as Boolean?

        assertEquals("None", sameSite)
        assertEquals(true, useSecureCookie)
    }
}
