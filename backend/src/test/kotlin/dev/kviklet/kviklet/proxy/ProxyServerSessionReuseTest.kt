// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.AuthenticatedClient
import dev.kviklet.kviklet.proxy.core.ProxyConnection
import dev.kviklet.kviklet.proxy.core.ProxyProtocol
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.Socket
import java.time.Instant

// Registry-only tests for the one-live-session-per-request reuse in registerSession; no listener (port -1),
// no wire protocol, no containers.
class ProxyServerSessionReuseTest {

    private val protocolStub = object : ProxyProtocol {
        override fun handshake(clientSocket: Socket, resolve: (String) -> ProxySession?): AuthenticatedClient? =
            throw UnsupportedOperationException("not used in registry tests")

        override fun connect(authenticatedClient: AuthenticatedClient): ProxyConnection =
            throw UnsupportedOperationException("not used in registry tests")

        override fun refuseOverCapacity(authenticatedClient: AuthenticatedClient) =
            throw UnsupportedOperationException("not used in registry tests")
    }

    private val requestFactory = ExecutionRequestFactory()
    private val server = ProxyServer(listenPort = -1, protocol = protocolStub)

    @AfterEach
    fun tearDown() {
        server.shutdownServer()
    }

    private fun session(request: ExecutionRequest, username: String) = ProxySession(
        username = username,
        password = "password-for-$username",
        executionRequest = request,
        userId = "mock",
        targetHost = "localhost",
        targetPort = 5432,
        databaseName = "testdb",
        datasourceType = DatasourceType.POSTGRESQL,
        authenticationDetails = AuthenticationDetails.UserPassword("test", "test"),
    )

    @Test
    fun `registering a second session for the same request returns the existing one`() {
        val request = requestFactory.createDatasourceExecutionRequest()

        val first = server.registerSession(session(request, "userA"), expiresAt = null)
        val second = server.registerSession(session(request, "userB"), expiresAt = null)

        // The caller gets the original session back, so repeated proxy calls hand out the same credentials
        // and a never-expiring request cannot accumulate registry entries.
        assertSame(first, second)
        assertEquals("userA", second.username)
    }

    @Test
    fun `a different request gets its own session`() {
        val first = server.registerSession(
            session(requestFactory.createDatasourceExecutionRequest(), "userA"),
            expiresAt = null,
        )
        val other = server.registerSession(
            session(requestFactory.createDatasourceExecutionRequest(), "userB"),
            expiresAt = null,
        )

        assertNotSame(first, other)
        assertEquals("userB", other.username)
    }

    @Test
    fun `after the request's session expires a new registration creates a fresh one`() {
        val request = requestFactory.createDatasourceExecutionRequest()
        server.registerSession(session(request, "userA"), expiresAt = Instant.now().minusSeconds(1))

        // Expiry fires asynchronously on the scheduler; until it has pruned the old session, registration
        // keeps returning it, afterwards a fresh session for the same request must win.
        val deadline = System.currentTimeMillis() + 5_000
        var replaced = false
        while (System.currentTimeMillis() < deadline) {
            val candidate = session(request, "userB")
            if (server.registerSession(candidate, expiresAt = null) === candidate) {
                replaced = true
                break
            }
            Thread.sleep(50)
        }
        assertTrue(replaced, "An expired session must not be reused for a new registration")
    }
}
