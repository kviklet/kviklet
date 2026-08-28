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
fun getPostgresContainerConnProps(): Properties {
    val props = Properties()
    props.setProperty("user", "test")
    props.setProperty("password", "test")
    return props
}

fun directConnectionFactory(postgresContainer: PostgreSQLContainer<Nothing>): Connection =
    DriverManager.getConnection(postgresContainer.jdbcUrl, getPostgresContainerConnProps())

fun proxyServerFactory(
    postgresContainer: PostgreSQLContainer<Nothing>,
    executionRequestAdapter: ExecutionRequestAdapter,
    eventAdapter: EventAdapter,
    tlsCertificate: TLSCertificate? = null,
    eventServiceOverride: EventServiceMock? = null,
    handshakeTimeoutMs: Int = 10_000,
): ProxyInstance {
    val connAuth = AuthenticationDetails.UserPassword("test", "test")
    val executionRequestFactory = ExecutionRequestFactory()
    val request = executionRequestFactory.createDatasourceExecutionRequest()
    val eventService = eventServiceOverride ?: EventServiceMock(executionRequestAdapter, eventAdapter, request)
    val port = (12000..20000).random()
    val proxy = ProxyServer(port, PostgresProtocol(eventService, tlsCertificate), handshakeTimeoutMs)
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
        ),
        expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
    )
    val proxyJdbcConnectionString = "jdbc:postgresql://localhost:$port/testdb"
    val proxyProps = Properties()
    proxyProps.setProperty("user", "proxyUser")
    proxyProps.setProperty("password", "proxyPassword")
    val proxyConnection = DriverManager.getConnection(proxyJdbcConnectionString, proxyProps)
    return ProxyInstance(port, proxyJdbcConnectionString, proxy, proxyConnection, eventService)
}
