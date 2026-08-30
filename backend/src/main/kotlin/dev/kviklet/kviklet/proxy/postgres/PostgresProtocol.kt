// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.core.AuthenticatedClient
import dev.kviklet.kviklet.proxy.core.ProxyConnection
import dev.kviklet.kviklet.proxy.core.ProxyProtocol
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.core.TLSCertificate
import dev.kviklet.kviklet.proxy.core.tlsCertificateFactory
import dev.kviklet.kviklet.proxy.core.writeAndFlush
import dev.kviklet.kviklet.proxy.postgres.messages.errorResponse
import dev.kviklet.kviklet.service.EventService
import java.net.Socket

// The Postgres wire-protocol half of the proxy, plugged into a generic ProxyServer. It knows how to run the
// Postgres startup/SASL handshake and how to open a Postgres upstream; the ProxyServer owns everything else
// (listener, session registry, back-pressure, lifecycle).
class PostgresProtocol(
    private val eventService: EventService,
    private val tlsCertificate: TLSCertificate? = tlsCertificateFactory(),
) : ProxyProtocol {

    companion object {
        // The SASL password used for an unknown username, so an unauthenticated probe runs the exact same
        // PBKDF2/HMAC verification path and fails identically to a wrong password -- no user enumeration.
        private const val UNKNOWN_USER_PLACEHOLDER_PASSWORD = "kviklet-no-such-session"
    }

    // Routes by the username in the startup message. The resolver returns null for an unknown username;
    // authenticateClient still runs the full SASL exchange with a placeholder password so a probe cannot tell
    // an unknown user from a wrong password. Returns null when the client goes away before authenticating.
    override fun handshake(clientSocket: Socket, resolve: (String) -> ProxySession?): AuthenticatedClient? =
        authenticateClient(
            clientSocket,
            this.tlsCertificate,
            UNKNOWN_USER_PLACEHOLDER_PASSWORD,
            resolve,
        )

    // Opens the Postgres upstream for the authenticated session, finishes the client-facing startup with the
    // real backend's parameters, then hands both sockets to the relay. Closes the upstream if anything after
    // the connect throws, so a half-set-up session never leaks a target connection.
    override fun connect(authenticatedClient: AuthenticatedClient): ProxyConnection {
        val session = authenticatedClient.session
        val targetFactory = TargetPostgresSocketFactory(
            session.authenticationDetails,
            session.databaseName,
            session.targetHost,
            session.targetPort,
        )
        val remotePgConn = targetFactory.createTargetPgConnection()
        val forwardSocket = remotePgConn.getPGStream().socket

        return try {
            finishClientStartup(authenticatedClient.socket, remotePgConn.getConnProps())
            // Blocking relay: no read timeout. Each direction parks in a blocking read until data arrives or the
            // socket is closed on teardown.
            authenticatedClient.socket.soTimeout = 0
            forwardSocket.soTimeout = 0
            // Pass the raw accepted socket as rawClientSocket: for a TLS client authenticatedClient.socket is an
            // SSLSocket layered over it with autoClose=false, and only closing the raw socket reliably unblocks
            // the relay threads on teardown.
            Connection(
                authenticatedClient.socket,
                forwardSocket,
                eventService,
                session.executionRequest,
                session.userId,
                rawClientSocket = authenticatedClient.rawClientSocket,
            )
        } catch (e: Exception) {
            // The upstream is open but the session never started, close it so it is not leaked.
            runCatching { forwardSocket.close() }
            throw e
        }
    }

    // The client has completed SASL but not yet received AuthenticationOk (that happens in connect()), so
    // an ErrorResponse here surfaces as a clean connect-time failure in the client -- the same 53300 a real
    // postgres over its connection limit reports.
    override fun refuseOverCapacity(authenticatedClient: AuthenticatedClient) {
        authenticatedClient.socket.getOutputStream().writeAndFlush(
            errorResponse("too many connections through the Kviklet proxy, try again later", "53300"),
        )
    }
}
