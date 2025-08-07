package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequest
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
) {
    private val threadPool = Executors.newCachedThreadPool()
    private val clientConnections: ArrayList<Connection> = arrayListOf()
    private lateinit var serverSocket: ServerSocket
    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"
    private val maxConnections = 15
    private var currentConnections = 0
    private var targetPostgres: TargetPostgresSocketFactory =
        TargetPostgresSocketFactory(authenticationDetails, databaseName, targetHost, targetPort)
    var isRunning: Boolean = false
        private set

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
        this.clientConnections.forEach { it.close() }
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
                e.printStackTrace()
            } finally {
                if (!clientSocket.isClosed) {
                    clientSocket.close()
                }
                currentConnections--
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        val remotePgConn = targetPostgres.createTargetPgConnection()
        val configuredSocket = setupClient(
            clientSocket,
            this.tlsCertificate,
            remotePgConn.getConnProps(),
            this.proxyUsername,
            this.proxyPassword,
        )
        val forwardSocket = remotePgConn.getPGStream().socket
        configuredSocket.soTimeout = 10
        forwardSocket.soTimeout = 10
        val clientConnection = Connection(configuredSocket, forwardSocket, eventService, executionRequest, userId)
        this.clientConnections.add(clientConnection)
        clientConnection.startHandling()
    }
}

fun getShutdownDate(startTime: LocalDateTime, maxTimeMinutes: Long): Date = Date.from(
    startTime
        .plusMinutes(maxTimeMinutes)
        .atZone(ZoneId.systemDefault())
        .toInstant(),
)
