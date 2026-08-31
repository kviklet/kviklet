// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.helpers

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.core.TLSCertificate
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.postgres.PostgresProtocol
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.*

class ProxyInstance(
    val port: Int,
    val connectionString: String,
    val proxy: ProxyServer,
    val connection: Connection,
    val eventService: EventServiceMock,
)

// A started proxy with a registered session but no client connection yet -- for tests where the first
// connection attempt is itself the assertion (e.g. an upstream TLS failure must refuse the client).
class ProxyServerHandle(
    val port: Int,
    val connectionString: String,
    val proxy: ProxyServer,
    val eventService: EventServiceMock,
) {
    fun connect(): Connection {
        val proxyProps = Properties()
        proxyProps.setProperty("user", "proxyUser")
        proxyProps.setProperty("password", "proxyPassword")
        return DriverManager.getConnection(connectionString, proxyProps)
    }
}
fun getPostgresContainerConnProps(): Properties {
    val props = Properties()
    props.setProperty("user", "test")
    props.setProperty("password", "test")
    return props
}

fun directConnectionFactory(postgresContainer: PostgreSQLContainer<Nothing>): Connection =
    DriverManager.getConnection(postgresContainer.jdbcUrl, getPostgresContainerConnProps())

fun startPostgresProxy(
    postgresContainer: PostgreSQLContainer<Nothing>,
    executionRequestAdapter: ExecutionRequestAdapter,
    eventAdapter: EventAdapter,
    tlsCertificate: TLSCertificate? = null,
    eventServiceOverride: EventServiceMock? = null,
    handshakeTimeoutMs: Int = 10_000,
    maxConnectionsPerSession: Int = 15,
    additionalOptions: String = "",
): ProxyServerHandle {
    val connAuth = AuthenticationDetails.UserPassword("test", "test")
    val executionRequestFactory = ExecutionRequestFactory()
    val request = executionRequestFactory.createDatasourceExecutionRequest()
    val eventService = eventServiceOverride ?: EventServiceMock(executionRequestAdapter, eventAdapter, request)
    val port = (12000..20000).random()
    val proxy = ProxyServer(
        port,
        PostgresProtocol(eventService, tlsCertificate),
        handshakeTimeoutMs,
        maxConnectionsPerSession,
    )
    proxy.start()
    waitForProxyStart(proxy)
    proxy.registerSession(
        ProxySession(
            username = "proxyUser",
            password = "proxyPassword",
            executionRequest = request,
            userId = "mock",
            targetHost = postgresContainer.host,
            targetPort = postgresContainer.getMappedPort(5432),
            databaseName = "testdb",
            datasourceType = DatasourceType.POSTGRESQL,
            authenticationDetails = connAuth,
            additionalOptions = additionalOptions,
        ),
        expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
    )
    return ProxyServerHandle(port, "jdbc:postgresql://localhost:$port/testdb", proxy, eventService)
}

fun proxyServerFactory(
    postgresContainer: PostgreSQLContainer<Nothing>,
    executionRequestAdapter: ExecutionRequestAdapter,
    eventAdapter: EventAdapter,
    tlsCertificate: TLSCertificate? = null,
    eventServiceOverride: EventServiceMock? = null,
    handshakeTimeoutMs: Int = 10_000,
    maxConnectionsPerSession: Int = 15,
    additionalOptions: String = "",
): ProxyInstance {
    val handle = startPostgresProxy(
        postgresContainer,
        executionRequestAdapter,
        eventAdapter,
        tlsCertificate,
        eventServiceOverride,
        handshakeTimeoutMs,
        maxConnectionsPerSession,
        additionalOptions,
    )
    return ProxyInstance(handle.port, handle.connectionString, handle.proxy, handle.connect(), handle.eventService)
}
