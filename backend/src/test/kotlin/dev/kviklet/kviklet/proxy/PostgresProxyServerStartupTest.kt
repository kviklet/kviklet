// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.postgres.PostgresProtocol
import dev.kviklet.kviklet.service.EventService
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.net.ServerSocket

// Pure unit checks of the listener's boot behaviour -- no Spring context or database needed, since neither
// path ever touches the event service or opens an upstream connection.
class PostgresProxyServerStartupTest {

    @Test
    fun `a non-positive port disables the listener and start is a no-op`() {
        val server = ProxyServer(-1, PostgresProtocol(mockk<EventService>(), null))
        server.start()
        assertFalse(server.isRunning, "A disabled port (<= 0) must not start the listener")
    }

    @Test
    fun `a bind failure leaves the server not running instead of crashing`() {
        // Occupy a port, then point the proxy at it: the bind must fail gracefully (logged and swallowed) so
        // the application still boots with the proxy merely unavailable, rather than dying on the bind.
        ServerSocket(0).use { taken ->
            val server = ProxyServer(taken.localPort, PostgresProtocol(mockk<EventService>(), null))
            server.start()
            assertFalse(server.isRunning, "A failed bind must leave the server not running")
        }
    }
}
