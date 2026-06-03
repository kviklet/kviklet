package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.proxy.postgres.INFINITE_ACCESS
import dev.kviklet.kviklet.proxy.postgres.TLSCertificate
import dev.kviklet.kviklet.proxy.postgres.getShutdownDate
import dev.kviklet.kviklet.proxy.postgres.tlsCertificateFactory
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import java.net.ServerSocket
import java.net.Socket
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.schedule

class MySqlProxy(
    datasourceType: DatasourceType,
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
    private val clientConnections: CopyOnWriteArrayList<MySqlConnection> = CopyOnWriteArrayList()
    private lateinit var serverSocket: ServerSocket
    private var proxyUsername = "mysql"
    private var proxyPassword = "mysql"
    private val maxConnections = 15
    private val currentConnections = AtomicInteger(0)
    private var targetMySql: TargetMySqlSocketFactory =
        TargetMySqlSocketFactory(datasourceType, authenticationDetails, databaseName, targetHost, targetPort)
    @Volatile var isRunning: Boolean = false
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
            if (currentConnections.get() >= maxConnections) {
                Thread.sleep(100)
                continue
            }

            val clientSocket = acceptClientConnection() ?: continue
            handleClientConnection(clientSocket)
        }
    }

    private fun handleClientConnection(clientSocket: Socket) {
        threadPool.submit {
            try {
                currentConnections.incrementAndGet()
                handleClient(clientSocket)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Guard for the case where handleClient threw before MySqlConnection took ownership
                if (!clientSocket.isClosed) clientSocket.close()
                currentConnections.decrementAndGet()
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        val remoteMySqlConn = targetMySql.createTargetMySqlConnection()
        val forwardSocket = remoteMySqlConn.socket
        try {
            val configuredSocket = setupClientMySql(
                clientSocket,
                this.tlsCertificate,
                this.proxyUsername,
                this.proxyPassword,
            )
            val clientConnection = MySqlConnection(configuredSocket, forwardSocket, eventService, executionRequest, userId)
            clientConnections.add(clientConnection)
            try {
                clientConnection.startHandling()
            } finally {
                clientConnections.remove(clientConnection)
            }
        } catch (e: Exception) {
            if (!forwardSocket.isClosed) forwardSocket.close()
            throw e
        }
    }
}
