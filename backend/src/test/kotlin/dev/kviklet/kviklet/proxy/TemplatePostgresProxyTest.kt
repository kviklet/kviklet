package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.proxy.helpers.ProxyInstance
import dev.kviklet.kviklet.proxy.helpers.directConnectionFactory
import dev.kviklet.kviklet.proxy.helpers.proxyServerFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection

@SpringBootTest
@ActiveProfiles("test")
@Disabled()
class TemplatePostgresProxyTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter
    private lateinit var directConnection: Connection
    private lateinit var proxy: ProxyInstance
    private lateinit var postgresContainer: PostgreSQLContainer<Nothing>

    @BeforeEach
    fun setup() {
        postgresContainer = PostgreSQLContainer<Nothing>("postgres:13").apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
        }
        postgresContainer.start()
        while (!postgresContainer.isRunning) {
            Thread.sleep(1000)
        }
        this.directConnection = directConnectionFactory(postgresContainer)
        this.proxy = proxyServerFactory(postgresContainer, executionRequestAdapter, eventAdapter)
    }

    @AfterEach
    fun tearDown() {
        this.proxy.proxy.shutdownServer()
        this.proxy.connection.close()
        this.postgresContainer.stop()
    }

    @Test
    fun `MockPostgresProxyTest`() {
        assert(true)
    }
}
