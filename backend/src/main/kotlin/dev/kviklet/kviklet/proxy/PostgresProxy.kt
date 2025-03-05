package dev.kviklet.kviklet.proxy

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

    private fun shutdownServer() {
        this.threadPool.shutdownNow()
        this.serverSocket.close()
        this.isRunning = false
    }

    private fun startTcpListener(port: Int, maxTimeMinutes: Long, startTime: LocalDateTime) {
        this.serverSocket = ServerSocket(port)
        this.isRunning = true
        while (true) {
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

    private fun acceptClientConnection(): Socket? {
        return try {
            serverSocket.accept()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun handleClient(clientSocket: Socket) {
        clientSocket.use { socket ->
            val remotePgConn = targetPostgres.createTargetPgConnection()
            remotePgConn.getPGStream().socket.use { forwardSocket ->
                forwardSocket.soTimeout = 10

                val conn = Connection(socket, forwardSocket, eventService, executionRequest, userId)
                conn.startHandling(proxyUsername,proxyPassword,remotePgConn.getConnProps())
            }
        }
    }
}

class TargetPostgresConnection(private val connInfo: Pair<PGStream, Map<String, String>>) {
    fun getPGStream() : PGStream { return connInfo.first }
    fun getConnProps() : Map<String, String> { return connInfo.second}
}
class TargetPostgresSocketFactory(authenticationDetails: AuthenticationDetails.UserPassword,
                                   databaseName: String,
                                   targetHost: String,
                                   targetPort: Int) {
    private val targetPgConnProps: Properties
    private val hostSpec: Array<HostSpec>
    init {
        val props = Properties()
        props.setProperty("user", authenticationDetails.username)
        props.setProperty("password", authenticationDetails.password)
        val database = if (databaseName != "") databaseName else authenticationDetails.username
        props.setProperty("PGDBNAME", database)

        this.targetPgConnProps = props
        this.hostSpec = arrayOf(HostSpec(targetHost, targetPort))
    }

    fun createTargetPgConnection(): TargetPostgresConnection {
        val factory = ConnectionFactoryImpl()
        val queryExecutor = factory.openConnectionImpl(
            this.hostSpec, this.targetPgConnProps
        ) as QueryExecutorBase

        val queryExecutorClass = QueryExecutorBase::class

        val pgStreamProperty = queryExecutorClass.memberProperties.firstOrNull { it.name == "pgStream" }
            ?: throw NoSuchElementException("Property 'pgStream' is not found")
        pgStreamProperty.isAccessible = true

        return TargetPostgresConnection(Pair(pgStreamProperty.get(queryExecutor) as PGStream, queryExecutor.parameterStatuses))
    }
}

fun isSessionExpired(sessionStartTime: LocalDateTime, maximumDuration: Long): Boolean {
    return maximumDuration <= 0L || utcTimeNow().isAfter(sessionStartTime.plusMinutes(maximumDuration))
}