// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.messages.startupMessageUser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

// The proxy must decide "is this the configured proxy user?" from the actual `user` parameter of the
// StartupMessage, not from a substring match over the whole packet (where the username can appear in the
// database name, application_name, or options and match spuriously).
class PostgresProxyStartupUserTest {

    // Builds a real StartupMessage: int32 length, int32 protocol 3.0, then null-terminated key/value
    // pairs terminated by a final null byte.
    private fun startupMessage(vararg params: Pair<String, String>): ByteArray {
        val body = ByteArrayBuilder()
        for ((key, value) in params) {
            body.appendCString(key)
            body.appendCString(value)
        }
        body.appendByte(0) // terminating null of the parameter list
        val payload = body.toByteArray()
        val buffer = ByteBuffer.allocate(8 + payload.size)
        buffer.putInt(8 + payload.size)
        buffer.putInt(196608) // protocol version 3.0
        buffer.put(payload)
        return buffer.array()
    }

    private class ByteArrayBuilder {
        private val out = java.io.ByteArrayOutputStream()
        fun appendCString(s: String) {
            out.write(s.toByteArray(Charsets.UTF_8))
            out.write(0)
        }
        fun appendByte(b: Int) = out.write(b)
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    @Test
    fun `the parsed user is the value of the user parameter`() {
        val frame = startupMessage("user" to "proxyUser", "database" to "somedb")

        assertEquals("proxyUser", startupMessageUser(frame, frame.size))
    }

    @Test
    fun `a wrong user is rejected even when the configured name appears elsewhere in the packet`() {
        // Configured proxy user is "app"; the client sends user=attacker but a database named "app_db",
        // so the old substring check matches "app" and wrongly reports the user as valid.
        val frame = startupMessage("user" to "attacker", "database" to "app_db")

        assertEquals("attacker", startupMessageUser(frame, frame.size))
        assertNotEquals(
            "app",
            startupMessageUser(frame, frame.size),
            "user=attacker must not authenticate as the configured proxy user 'app'",
        )
    }

    @Test
    fun `the correct user is accepted`() {
        val frame = startupMessage("user" to "app", "database" to "app_db")

        assertEquals("app", startupMessageUser(frame, frame.size))
    }

    @Test
    fun `a value equal to user does not fool the parser into returning the wrong key`() {
        // A parameter whose value is literally "user", followed by a key named like the proxy user, must not
        // trick a flat indexOf into returning that following key. The real user is "attacker".
        val frame = startupMessage("database" to "user", "app" to "x", "user" to "attacker")

        assertEquals("attacker", startupMessageUser(frame, frame.size))
        assertNotEquals("app", startupMessageUser(frame, frame.size))
    }

    @Test
    fun `an empty-valued parameter before user does not terminate the scan early`() {
        // application_name="" is a legitimate empty value; it must not end the parameter scan before `user`.
        val frame = startupMessage("application_name" to "", "user" to "proxyUser")

        assertEquals("proxyUser", startupMessageUser(frame, frame.size))
    }

    @Test
    fun `a startup message without a user parameter has no user`() {
        val frame = startupMessage("database" to "somedb")

        assertNull(startupMessageUser(frame, frame.size))
    }
}
