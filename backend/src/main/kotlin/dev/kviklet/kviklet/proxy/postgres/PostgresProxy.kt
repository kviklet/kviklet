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
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.concurrent.schedule

// When temporaryAccessDuration is null, it indicates infinite access
// This constant is used to represent that case in the proxy
val INFINITE_ACCESS = -1L
class PostgresProxy(
    targetHost: String,
    targetPort: Int,
    databaseName: String,
    authenticationDetails: AuthenticationDetails.UserPassword,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
    private val tlsCertificate: TLSCertificate? = tlsCertificateFactory(),
    // Bounds every read during the handshake, so an idle or aborting client cannot pin a slot (or a
    // core) forever. It caps each individual read, not the whole handshake, so it can be tight.
    private val handshakeTimeoutMs: Int = 10_000,
) {
    private val threadPool = Executors.newCachedThreadPool()

    // Concurrent by nature: pool threads add on setup and remove on teardown while the scheduled
    // shutdown iterates it. A plain list would corrupt or lose elements under that race, which for a
    // time-boxed access proxy means a live session could survive past its window or shutdown could
    // fault before closing the port. Broader lifecycle/I-O-model alignment stays with KVI-221.
    private val clientConnections = CopyOnWriteArrayList<Connection>()
    private lateinit var serverSocket: ServerSocket
    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"
    private val maxConnections = 15

    // Volatile so tests and monitoring see slot releases done by the connection handler threads
    @Volatile
    var currentConnections = 0
        private set

    private var targetPostgres: TargetPostgresSocketFactory =
        TargetPostgresSocketFactory(authenticationDetails, databaseName, targetHost, targetPort)

    // Read by the listener loop and by handleClient on pool threads, written by shutdownServer, so it
    // needs a happens-before edge for those threads to see the shutdown promptly.
    @Volatile
    var isRunning: Boolean = false
        private set

    companion object {
        private val logger = LoggerFactory.getLogger(PostgresProxy::class.java)
    }

    fun startServer(
        port: Int,
        proxyUsername: String,
        proxyPassword: String,
        startTime: LocalDateTime,
        maxTimeMinutes: Long,
    ) {
        this.proxyUsername = proxyUsername
        this.proxyPassword = proxyPassword
        Thread { this.startTcpListener(port) }.start()
        if (maxTimeMinutes != INFINITE_ACCESS) {
            scheduleShutdown(
                getShutdownDate(startTime, maxTimeMinutes),
            )
        }
    }

    private fun scheduleShutdown(shutdownTime: Date) {
        Timer().schedule(shutdownTime) {
            shutdownServer()
        }
    }

    // Synchronized against registerConnection so the two cannot interleave: a session is either added
    // to the list before this iterates it (and so gets closed here) or refused by registerConnection
    // (and torn down by its caller). shutdownNow does not wait on the worker threads, so holding the
    // monitor here cannot deadlock against a worker blocked in registerConnection.
    @Synchronized
    fun shutdownServer() {
        this.isRunning = false
        // CopyOnWriteArrayList gives a stable snapshot to iterate even as sessions prune themselves.
        this.clientConnections.forEach { it.close() }
        this.threadPool.shutdownNow()
        this.serverSocket.close()
    }

    // Adds a fully set-up session to the tracking list, but only if the proxy is still running. Returns
    // false when the proxy shut down mid-handshake, so the caller tears the new session down instead of
    // leaving it relaying past the access window (nothing else would ever close it).
    @Synchronized
    private fun registerConnection(connection: Connection): Boolean {
        if (!isRunning) {
            return false
        }
        clientConnections.add(connection)
        return true
    }

    private fun acceptClientConnection(): Socket? = try {
        serverSocket.accept()
    } catch (e: Exception) {
        null
    }

    private fun startTcpListener(port: Int) {
        this.serverSocket = ServerSocket(port)
        this.isRunning = true
        startListeningLoop()
    }

    private fun startListeningLoop() {
        while (this.isRunning) {
            if (currentConnections >= maxConnections) {
                Thread.sleep(1000)
                continue
            }

            val clientSocket = acceptClientConnection() ?: continue
            handleClientConnection(clientSocket)
        }
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

        // Authenticate the client first. No upstream connection exists yet, so an unauthenticated,
        // aborting or idle client cannot leak a target DB connection (KVI-228).
        val authenticatedClient = authenticateClient(
            clientSocket,
            this.tlsCertificate,
            this.proxyUsername,
            this.proxyPassword,
        ) ?: return // client went away or asked to cancel; nothing to relay

        // If the proxy shut down while the client was still handshaking, the access window has closed;
        // do not open a credentialed upstream connection. registerConnection re-checks this atomically
        // below to also cover a shutdown that fires during the upstream connect itself.
        if (!isRunning) return

        // Only now that the client is authenticated do we open the upstream connection.
        val remotePgConn = targetPostgres.createTargetPgConnection()
        val forwardSocket = remotePgConn.getPGStream().socket

        val clientConnection = try {
            finishClientStartup(authenticatedClient.socket, remotePgConn.getConnProps())
            authenticatedClient.socket.soTimeout = 10
            forwardSocket.soTimeout = 10
            Connection(authenticatedClient.socket, forwardSocket, eventService, executionRequest, userId)
        } catch (e: Exception) {
            // The upstream is open but the session never started, close it so it is not leaked.
            runCatching { forwardSocket.close() }
            throw e
        }

        // Register atomically against shutdown: if the proxy shut down after the isRunning check above
        // (e.g. during the upstream connect), this returns false and we close the just-opened session
        // rather than leaving it running past the access window.
        if (!registerConnection(clientConnection)) {
            clientConnection.close()
            return
        }
        try {
            clientConnection.startHandling()
        } finally {
            this.clientConnections.remove(clientConnection)
        }
    }
}

fun getShutdownDate(startTime: LocalDateTime, maxTimeMinutes: Long): Date = Date.from(
    startTime
        .plusMinutes(maxTimeMinutes)
        .atZone(ZoneId.systemDefault())
        .toInstant(),
)
