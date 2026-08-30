// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.proxy.core.AuthenticatedClient
import dev.kviklet.kviklet.proxy.core.ProxyConnection
import dev.kviklet.kviklet.proxy.core.ProxyProtocol
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.core.TLSCertificate
import dev.kviklet.kviklet.proxy.core.tlsCertificateFactory
import dev.kviklet.kviklet.service.EventService
import java.net.Socket

// The MySQL/MariaDB wire-protocol half of the proxy, plugged into a generic ProxyServer. It knows how to
// run the MySQL handshake and how to open a MySQL or MariaDB upstream (the two share one wire protocol, so
// one listener serves both; the session's datasourceType records which flavor the target is); the
// ProxyServer owns everything else (listener, session registry, back-pressure, lifecycle).
class MySqlProtocol(
    private val eventService: EventService,
    private val tlsCertificate: TLSCertificate? = tlsCertificateFactory(),
) : ProxyProtocol {

    companion object {
        // The password used for an unknown username, so an unauthenticated probe runs the exact same
        // scramble verification path and fails identically to a wrong password -- no user enumeration.
        private const val UNKNOWN_USER_PLACEHOLDER_PASSWORD = "kviklet-no-such-session"
    }

    // Routes by the username in the client's HandshakeResponse. The resolver returns null for an unknown
    // username; authenticateClientMySql still runs the full verification with a placeholder password so a
    // probe cannot tell an unknown user from a wrong password. Returns null when the client goes away
    // before authenticating.
    override fun handshake(clientSocket: Socket, resolve: (String) -> ProxySession?): AuthenticatedClient? =
        authenticateClientMySql(
            clientSocket,
            this.tlsCertificate,
            UNKNOWN_USER_PLACEHOLDER_PASSWORD,
            resolve,
        )

    // Opens the MySQL/MariaDB upstream for the authenticated session and hands both sockets to the relay.
    // The client already received its OK packet during the handshake (MySQL has no post-auth parameter
    // phase like Postgres), so all that remains is the upstream connect. Closes the upstream if anything
    // after the connect throws, so a half-set-up session never leaks a target connection.
    override fun connect(authenticatedClient: AuthenticatedClient): ProxyConnection {
        val session = authenticatedClient.session
        val targetFactory = TargetMySqlSocketFactory(
            session.datasourceType,
            session.authenticationDetails,
            session.databaseName,
            session.targetHost,
            session.targetPort,
        )
        val targetConnection = targetFactory.createTargetMySqlConnection()
        val forwardSocket = targetConnection.socket

        return try {
            // Blocking relay: no read timeout. Each direction parks in a blocking read until data arrives
            // or the socket is closed on teardown.
            authenticatedClient.socket.soTimeout = 0
            forwardSocket.soTimeout = 0
            // Pass the raw accepted socket as rawClientSocket: for a TLS client authenticatedClient.socket
            // is an SSLSocket layered over it, and only closing the raw socket reliably unblocks the relay
            // threads on teardown.
            MySqlConnection(
                authenticatedClient.socket,
                forwardSocket,
                eventService,
                session.executionRequest,
                session.userId,
                rawClientSocket = authenticatedClient.rawClientSocket,
                upstreamJdbcConnection = targetConnection.jdbcConnection,
            )
        } catch (e: Exception) {
            // The upstream is open but the session never started, close it so it is not leaked.
            runCatching { forwardSocket.close() }
            runCatching { targetConnection.jdbcConnection.close() }
            throw e
        }
    }

    // The MySQL handshake has already answered the client with its OK packet, so the refusal is delivered
    // as the ERR the client reads in response to its first command (1040 Too many connections) -- still a
    // clear error instead of the silent close it used to get. Sequence id 1 matches a first-command reply.
    override fun refuseOverCapacity(authenticatedClient: AuthenticatedClient) {
        writePacket(
            authenticatedClient.socket.getOutputStream(),
            1,
            buildErrPacket(1040, "08004", "Too many connections through the Kviklet proxy, try again later"),
        )
    }
}
