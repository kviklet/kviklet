package dev.kviklet.kviklet.proxy.helpers

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.postgres.PostgresProxy
import dev.kviklet.kviklet.proxy.postgres.TLSCertificate
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

class ProxyInstance(
    val port: Int,
    val connectionString: String,
    val proxy: PostgresProxy,
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
): ProxyInstance {
    val connAuth = AuthenticationDetails.UserPassword("test", "test")
    val executionRequestFactory = ExecutionRequestFactory()
    val request = executionRequestFactory.createDatasourceExecutionRequest()
    val eventService = EventServiceMock(executionRequestAdapter, eventAdapter, request)
    var proxy = PostgresProxy(
        postgresContainer.host,
        postgresContainer.getMappedPort(5432),
        "testdb",
        connAuth,
        eventService,
        request,
        "mock",
        tlsCertificate,
    )
    val port = (12000..20000).random()
    CompletableFuture.runAsync {
        proxy.startServer(port, "proxyUser", "proxyPassword", LocalDateTime.now(), 10)
    }
    waitForProxyStart(proxy)
    val proxyJdbcConnectionString = "jdbc:postgresql://localhost:$port/testdb"
    val proxyProps = Properties()
    proxyProps.setProperty("user", "proxyUser")
    proxyProps.setProperty("password", "proxyPassword")
    val proxyPort = port
    val proxyConnection = DriverManager.getConnection("jdbc:postgresql://localhost:$port/testdb", proxyProps)
    return ProxyInstance(proxyPort, proxyJdbcConnectionString, proxy, proxyConnection, eventService)
}
