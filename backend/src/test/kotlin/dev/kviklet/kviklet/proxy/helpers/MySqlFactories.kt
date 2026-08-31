// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.helpers

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.core.TLSCertificate
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.mysql.MySqlProtocol
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import org.testcontainers.containers.JdbcDatabaseContainer
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.Properties

class MySqlProxyInstance(
    val port: Int,
    val connectionString: String,
    val proxy: ProxyServer,
    val eventService: EventServiceMock,
    val username: String,
    val password: String,
) {
    fun connect(user: String = username, pass: String = password): Connection = DriverManager.getConnection(
        connectionString,
        Properties().apply {
            setProperty("user", user)
            setProperty("password", pass)
        },
    )
}

// The client-side JDBC URL for connecting THROUGH the proxy: the matching driver per flavor, with TLS off
// (the plain-test proxy does not advertise CLIENT_SSL).
fun mysqlClientJdbcUrl(datasourceType: DatasourceType, port: Int, extraParams: String = ""): String = when (
    datasourceType
) {
    DatasourceType.MYSQL -> "jdbc:mysql://localhost:$port/testdb?sslMode=DISABLED$extraParams"
    DatasourceType.MARIADB -> "jdbc:mariadb://localhost:$port/testdb?sslMode=disable$extraParams"
    else -> throw IllegalArgumentException("$datasourceType is not served by the MySQL proxy")
}

fun mysqlDirectConnectionFactory(container: JdbcDatabaseContainer<*>): Connection = DriverManager.getConnection(
    container.jdbcUrl,
    Properties().apply {
        setProperty("user", "test")
        setProperty("password", "test")
    },
)

fun mysqlProxyServerFactory(
    container: JdbcDatabaseContainer<*>,
    datasourceType: DatasourceType,
    executionRequestAdapter: ExecutionRequestAdapter,
    eventAdapter: EventAdapter,
    tlsCertificate: TLSCertificate? = null,
    eventServiceOverride: EventServiceMock? = null,
    handshakeTimeoutMs: Int = 10_000,
    username: String = "proxyUser",
    password: String = "proxyPassword",
    maxConnectionsPerSession: Int = 15,
    additionalOptions: String = "",
): MySqlProxyInstance {
    val connAuth = AuthenticationDetails.UserPassword("test", "test")
    val executionRequestFactory = ExecutionRequestFactory()
    val request = executionRequestFactory.createDatasourceExecutionRequest()
    val eventService = eventServiceOverride ?: EventServiceMock(executionRequestAdapter, eventAdapter, request)
    val port = (12000..20000).random()
    val proxy = ProxyServer(
        port,
        MySqlProtocol(eventService, tlsCertificate),
        handshakeTimeoutMs,
        maxConnectionsPerSession,
    )
    proxy.start()
    waitForProxyStart(proxy)
    proxy.registerSession(
        ProxySession(
            username = username,
            password = password,
            executionRequest = request,
            userId = "mock",
            targetHost = container.host,
            targetPort = container.getMappedPort(3306),
            databaseName = "testdb",
            datasourceType = datasourceType,
            authenticationDetails = connAuth,
            additionalOptions = additionalOptions,
        ),
        expiresAt = Instant.now().plus(Duration.ofMinutes(10)),
    )
    return MySqlProxyInstance(port, mysqlClientJdbcUrl(datasourceType, port), proxy, eventService, username, password)
}
