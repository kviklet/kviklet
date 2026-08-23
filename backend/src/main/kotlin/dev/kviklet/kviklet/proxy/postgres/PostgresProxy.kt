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
    private val clientConnections: ArrayList<Connection> = arrayListOf()
    private lateinit var serverSocket: ServerSocket
    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"
    private val maxConnections = 15

    // Volatile so tests and monitoring see slot releases done by the connection handler threads
    @Volatile
    var currentConnections = 0
        private set

    // Number of sessions currently tracked in clientConnections. Once a session ends it is pruned, so
    // this returns to baseline rather than growing for the lifetime of the proxy.
    val trackedConnectionCount: Int
        get() = clientConnections.size
    private var targetPostgres: TargetPostgresSocketFactory =
        TargetPostgresSocketFactory(authenticationDetails, databaseName, targetHost, targetPort)
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

    fun shutdownServer() {
        this.isRunning = false
        // Snapshot the list: each session prunes itself from clientConnections as it ends, so iterating
        // the live list while sessions are shutting down would risk a concurrent modification.
        ArrayList(this.clientConnections).forEach { it.close() }
        this.threadPool.shutdownNow()
        this.serverSocket.close()
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

        this.clientConnections.add(clientConnection)
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
