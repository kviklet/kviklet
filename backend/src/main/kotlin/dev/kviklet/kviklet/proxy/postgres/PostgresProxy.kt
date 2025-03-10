package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.postgresql.core.PGStream
import org.postgresql.core.QueryExecutorBase
import org.postgresql.core.v3.ConnectionFactoryImpl
import org.postgresql.util.HostSpec
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class PostgresProxy(
    targetHost: String,
    targetPort: Int,
    databaseName: String,
    authenticationDetails: AuthenticationDetails.UserPassword,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
    private val tlsCertificate: TLSCertificate? = null
) {
    private val threadPool = Executors.newCachedThreadPool()
    private lateinit var serverSocket: ServerSocket
    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"
    private val maxConnections = 15
    private var currentConnections = 0
    private var targetPostgres : TargetPostgresSocketFactory =
        TargetPostgresSocketFactory(authenticationDetails, databaseName, targetHost, targetPort)
    var isRunning : Boolean = false
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
        Thread { this.startTcpListener(port, maxTimeMinutes, startTime) }.start()
    }

    fun shutdownServer() {
        this.threadPool.shutdownNow()
        this.serverSocket.close()
        this.isRunning = false
    }
    private fun acceptClientConnection(): Socket? {
        return try {
            serverSocket.accept()
        } catch (e: Exception) {
            null
        }
    }
    private fun startTcpListener(port: Int, maxTimeMinutes: Long, startTime: LocalDateTime) {
        this.serverSocket = ServerSocket(port)
        this.isRunning = true
        startListeningLoop(maxTimeMinutes, startTime)
    }

    private fun startListeningLoop(maxTimeMinutes: Long, startTime: LocalDateTime) {
        while (this.isRunning) {
            if (isSessionExpired(startTime, maxTimeMinutes)) {
                this.shutdownServer()
                break
            }
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
        val configuredSocket = setupClient(clientSocket, this.tlsCertificate, remotePgConn.getConnProps(), this.proxyUsername, this.proxyPassword)
        val forwardSocket =  remotePgConn.getPGStream().socket
        configuredSocket.soTimeout = 10
        forwardSocket.soTimeout = 10
        Connection(configuredSocket, forwardSocket, eventService, executionRequest, userId)
            .startHandling()
    }
}

fun isSessionExpired(sessionStartTime: LocalDateTime, maximumDuration: Long): Boolean {
    return maximumDuration <= 0L || utcTimeNow().isAfter(sessionStartTime.plusMinutes(maximumDuration))
}