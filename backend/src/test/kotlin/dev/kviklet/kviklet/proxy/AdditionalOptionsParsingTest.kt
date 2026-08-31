// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.core.parseAdditionalOptions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdditionalOptionsParsingTest {

    @Test
    fun `an empty or blank string parses to no options`() {
        assertEquals(emptyMap<String, String>(), parseAdditionalOptions(""))
        assertEquals(emptyMap<String, String>(), parseAdditionalOptions("  "))
        assertEquals(emptyMap<String, String>(), parseAdditionalOptions("?"))
    }

    @Test
    fun `a query string tail parses into its key value pairs`() {
        assertEquals(
            mapOf("sslmode" to "verify-full", "sslrootcert" to "/etc/certs/ca.pem"),
            parseAdditionalOptions("?sslmode=verify-full&sslrootcert=/etc/certs/ca.pem"),
        )
    }

    @Test
    fun `the leading question mark is optional`() {
        assertEquals(mapOf("sslMode" to "trust"), parseAdditionalOptions("sslMode=trust"))
    }

    @Test
    fun `values are url-decoded like the JDBC drivers decode them`() {
        assertEquals(
            mapOf("sslrootcert" to "/etc/my certs/ca.pem"),
            parseAdditionalOptions("?sslrootcert=/etc/my%20certs/ca.pem"),
        )
    }

    @Test
    fun `malformed entries are skipped rather than failing the parse`() {
        assertEquals(
            mapOf("sslmode" to "require"),
            parseAdditionalOptions("?sslmode=require&novalue&=orphan&"),
        )
    }

    @Test
    fun `an empty value is kept as an empty string`() {
        assertEquals(mapOf("sslmode" to ""), parseAdditionalOptions("?sslmode="))
    }
}
