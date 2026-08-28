// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.slf4j.LoggerFactory
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Timer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.concurrent.schedule

// When temporaryAccessDuration is null, it indicates infinite access. This constant represents that case.
val INFINITE_ACCESS = -1L

// A single proxied access session: the temp credentials a client authenticates with, the upstream it is
// routed to, and the audit context. Sessions are keyed in the server registry by [username], which the
// client delivers in its startup message before any upstream work happens -- that is exactly what lets one
// listener serve many concurrent requests without a port per request.
class ProxySession(
    val username: String,
    val password: String,
    val targetFactory: TargetPostgresSocketFactory,
    val executionRequest: ExecutionRequest,
    val userId: String,
) {
    // Live relay connections for this session, closed when the session expires so an in-flight client
    // cannot keep relaying past the access window. CopyOnWriteArrayList: added by handler threads, iterated
    // by the expiry timer / shutdown.
    val connections = CopyOnWriteArrayList<Connection>()

    // Flipped to false on expiry or server shutdown. Read during auth routing and connection registration
    // (both under the server monitor) so a connection that authenticated just as the window closed is
    // refused instead of left relaying.
    @Volatile
    var active = true
        internal set

    internal var expiryTimer: Timer? = null
}

// A long-lived proxy server: one listener on a single stable port that routes every authenticated client to
// its session by the temp username. Replaces the previous one-proxy-per-port model, so shutting down a
// *session* (its access window expiring) no longer shuts down the *server*. Broader lifecycle/I-O-model
// alignment with the MySQL proxy stays with KVI-221.
class PostgresProxyServer(
    // The stable listener port. A value <= 0 disables the listener entirely (used in tests and any build
    // where the proxy is not wired up); start() then becomes a no-op.
    val listenPort: Int,
    private val eventService: EventService,
    private val tlsCertificate: TLSCertificate? = tlsCertificateFactory(),
    // Bounds every read during the handshake, so an idle or aborting client cannot pin a slot (or a core)
    // forever. It caps each individual read, not the whole handshake, so it can be tight.
    private val handshakeTimeoutMs: Int = 10_000,
) {
    private val threadPool = Executors.newCachedThreadPool()

    // Registry keyed by temp username. ConcurrentHashMap because sessions are added by the request thread,
    // read by connection-handler threads during auth routing, and removed by expiry timers.
    private val sessions = ConcurrentHashMap<String, ProxySession>()

    // Every live relay connection across all sessions, iterated on shutdown. Concurrent by nature: handler
    // threads add/remove while shutdown iterates.
    private val clientConnections = CopyOnWriteArrayList<Connection>()

    private lateinit var serverSocket: ServerSocket
    private val maxConnections = 15

    // Volatile so tests and monitoring see slot releases done by the connection handler threads.
    @Volatile
    var currentConnections = 0
        private set

    // Read by the accept loop and by handleClient on pool threads, written by start/shutdownServer, so it
    // needs a happens-before edge for those threads to see the state change promptly.
    @Volatile
    var isRunning: Boolean = false
        private set

    companion object {
        private val logger = LoggerFactory.getLogger(PostgresProxyServer::class.java)

        // The SASL password used for an unknown username, so an unauthenticated probe runs the exact same
        // PBKDF2/HMAC verification path and fails identically to a wrong password -- no user enumeration.
        private const val UNKNOWN_USER_PLACEHOLDER_PASSWORD = "kviklet-no-such-session"
    }

    // Binds the listener and starts accepting on a background thread. Bind failure is logged and swallowed:
    // the application must still boot (the proxy feature is simply unavailable -- e.g. the port is already
    // taken in local dev) rather than crash in a bare thread's default handler. A disabled port is a no-op.
    fun start() {
        if (listenPort <= 0) {
            logger.info("Postgres proxy listener disabled (port $listenPort); proxy access is unavailable")
            return
        }
        try {
            serverSocket = ServerSocket(listenPort)
        } catch (e: Exception) {
            logger.error("Postgres proxy could not bind port $listenPort; proxy access will be unavailable", e)
            return
        }
        isRunning = true
        Thread { acceptLoop() }.start()
        logger.info("Postgres proxy listening on port $listenPort")
    }

    // Registers (replacing any prior session for the same username) and schedules its expiry. Called from
    // the service layer when an approved request starts proxying. maxTimeMinutes == INFINITE_ACCESS never
    // expires. Synchronized against registerConnection/shutdown so a replaced session is torn down cleanly.
    @Synchronized
    fun registerSession(
        username: String,
        password: String,
        targetHost: String,
        targetPort: Int,
        databaseName: String,
        authenticationDetails: AuthenticationDetails.UserPassword,
        executionRequest: ExecutionRequest,
        userId: String,
        startTime: LocalDateTime,
        maxTimeMinutes: Long,
    ) {
        val session = ProxySession(
            username,
            password,
            TargetPostgresSocketFactory(authenticationDetails, databaseName, targetHost, targetPort),
            executionRequest,
            userId,
        )
        // Replace any prior session for the same username, tearing the old one down so a re-proxied request
        // never leaves the previous access window running.
        sessions.put(username, session)?.let { expire(it) }
        if (maxTimeMinutes != INFINITE_ACCESS) {
            val timer = Timer()
            timer.schedule(getShutdownDate(startTime, maxTimeMinutes)) {
                expireSession(username)
            }
            session.expiryTimer = timer
        }
    }

    // Stops the whole server: no more sessions, every live relay closed, listener closed. Synchronized
    // against registerConnection/registerSession so a session is either registered before this iterates (and
    // closed here) or refused afterwards. shutdownNow does not wait on workers, so holding the monitor here
    // cannot deadlock against a worker blocked entering registerConnection.
    @Synchronized
    fun shutdownServer() {
        isRunning = false
        sessions.values.forEach {
            it.active = false
            it.expiryTimer?.cancel()
        }
        sessions.clear()
        clientConnections.forEach { it.close() }
        threadPool.shutdownNow()
        if (::serverSocket.isInitialized) {
            serverSocket.close()
        }
    }

    // Only sessions that are still active resolve; expiry removes them from the map and flips active, so a
    // client that arrives after its window closed finds nothing and is rejected.
    private fun resolveSession(username: String): ProxySession? = sessions[username]?.takeIf { it.active }

    private fun expireSession(username: String) {
        sessions.remove(username)?.let { expire(it) }
    }

    // Synchronized against registerConnection so it cannot miss a connection that is being added at the same
    // moment: the connection is either added before this iterates (and closed here) or refused because
    // active is already false.
    @Synchronized
    private fun expire(session: ProxySession) {
        session.active = false
        session.expiryTimer?.cancel()
        session.connections.forEach { it.close() }
    }

    private fun acceptLoop() {
        while (isRunning) {
            if (currentConnections >= maxConnections) {
                Thread.sleep(1000)
                continue
            }
            val clientSocket = acceptClientConnection() ?: continue
            handleClientConnection(clientSocket)
        }
    }

    private fun acceptClientConnection(): Socket? = try {
        serverSocket.accept()
    } catch (e: Exception) {
        null
    }

    private fun handleClientConnection(clientSocket: Socket) {
        threadPool.submit {
            try {
                currentConnections++
                handleClient(clientSocket)
            } catch (e: Exception) {
                logger.warn("Error handling proxy client connection", e)
            } finally {
                if (!clientSocket.isClosed) {
                    clientSocket.close()
                }
                currentConnections--
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        // Deadline applies to every read below, before any credentialed upstream work happens.
        clientSocket.soTimeout = handshakeTimeoutMs

        // Authenticate first, routing by the username in the startup message. No upstream connection exists
        // yet, so an unauthenticated, aborting or idle client cannot leak a target DB connection (KVI-228).
        // The resolver returns null for an unknown username; authenticateClient still runs the full SASL
        // exchange with a placeholder password so a probe cannot tell an unknown user from a wrong password.
        val authenticatedClient = authenticateClient(
            clientSocket,
            this.tlsCertificate,
            UNKNOWN_USER_PLACEHOLDER_PASSWORD,
        ) { username -> resolveSession(username) } ?: return // client went away or asked to cancel

        val session = authenticatedClient.session

        // The access window may have closed (server shutdown or session expiry) while the client was still
        // handshaking; do not open a credentialed upstream connection. registerConnection re-checks this
        // atomically below to also cover a close that fires during the upstream connect itself.
        if (!isRunning || !session.active) return

        // Only now that the client is authenticated do we open the upstream connection for its session.
        val remotePgConn = session.targetFactory.createTargetPgConnection()
        val forwardSocket = remotePgConn.getPGStream().socket

        val clientConnection = try {
            finishClientStartup(authenticatedClient.socket, remotePgConn.getConnProps())
            // Blocking relay: no read timeout. Each direction parks in a blocking read until data arrives or
            // the socket is closed on teardown.
            authenticatedClient.socket.soTimeout = 0
            forwardSocket.soTimeout = 0
            // Pass the raw accepted socket as rawClientSocket: for a TLS client authenticatedClient.socket is
            // an SSLSocket layered over clientSocket with autoClose=false, and only closing the raw socket
            // reliably unblocks the relay threads on teardown.
            Connection(
                authenticatedClient.socket,
                forwardSocket,
                eventService,
                session.executionRequest,
                session.userId,
                rawClientSocket = clientSocket,
            )
        } catch (e: Exception) {
            // The upstream is open but the session never started, close it so it is not leaked.
            runCatching { forwardSocket.close() }
            throw e
        }

        // Register atomically against shutdown and expiry: if either fired after the check above (e.g. during
        // the upstream connect), this returns false and we close the just-opened session rather than leaving
        // it relaying past the access window.
        if (!registerConnection(session, clientConnection)) {
            clientConnection.close()
            return
        }
        try {
            clientConnection.startHandling()
        } finally {
            session.connections.remove(clientConnection)
            clientConnections.remove(clientConnection)
        }
    }

    // Adds a fully set-up relay to both the session and the global tracking list, but only if the server is
    // still running and the session still active. Returns false otherwise so the caller tears the new
    // session down instead of leaving it relaying past the access window (nothing else would ever close it).
    @Synchronized
    private fun registerConnection(session: ProxySession, connection: Connection): Boolean {
        if (!isRunning || !session.active) {
            return false
        }
        clientConnections.add(connection)
        session.connections.add(connection)
        return true
    }
}

fun getShutdownDate(startTime: LocalDateTime, maxTimeMinutes: Long): Date = Date.from(
    startTime
        .plusMinutes(maxTimeMinutes)
        .atZone(ZoneId.systemDefault())
        .toInstant(),
)
