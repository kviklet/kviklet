package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.mysql.MySqlProxy
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.db.ExecutePayload
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.sql.DriverManager
import java.time.LocalDateTime
import java.util.Properties
import java.util.concurrent.CompletableFuture

@Disabled("Requires a running local MySQL instance on port 33066")
class ManualIntegrationTest {

    @Test
    fun testProxyAuditLoggingEndToEnd() {
        val connAuth = AuthenticationDetails.UserPassword("test", "test")
        val executionRequestFactory = ExecutionRequestFactory()
        val request = executionRequestFactory.createDatasourceExecutionRequest()
        
        val capturedQueries = mutableListOf<String>()
        val eventService = mockk<EventService>(relaxed = true)
        every { eventService.saveEvent(any(), any(), any()) } answers {
            val payload = thirdArg<dev.kviklet.kviklet.db.Payload>()
            if (payload is ExecutePayload) {
                payload.query?.let { capturedQueries.add(it) }
            }
            mockk(relaxed = true)
        }

        val proxy = MySqlProxy(
            targetHost = "127.0.0.1",
            targetPort = 33066,
            databaseName = "testdb",
            authenticationDetails = connAuth,
            eventService = eventService,
            executionRequest = request,
            userId = "integration-test-user"
        )

        val port = 13307
        val tempUser = "proxyUser"
        val tempPassword = "proxyPassword123"

        CompletableFuture.runAsync {
            proxy.startServer(port, tempUser, tempPassword, LocalDateTime.now(), 10)
        }
        
        // Wait for proxy to start
        var sleepCycle = 0
        while (!proxy.isRunning && sleepCycle < 10) {
            Thread.sleep(1000)
            sleepCycle++
        }
        assert(proxy.isRunning)

        // Connect via JDBC to the Proxy
        val props = Properties()
        props.setProperty("user", tempUser)
        props.setProperty("password", tempPassword)
        Class.forName("com.mysql.cj.jdbc.Driver")
        
        val conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1:$port/testdb?sslMode=DISABLED&allowPublicKeyRetrieval=true", props)
        conn.use {
            val stmt = conn.createStatement()
            stmt.execute("CREATE TABLE IF NOT EXISTS auto_test (id INT)")
            stmt.execute("INSERT INTO auto_test VALUES (42)")
            val rs = stmt.executeQuery("SELECT * FROM auto_test")
            rs.close()
            stmt.execute("DROP TABLE auto_test")
        }

        proxy.shutdownServer()

        // Assert that the queries were audited successfully!
        println("Captured Queries: $capturedQueries")
        assertEquals(4, capturedQueries.size)
        assertEquals("CREATE TABLE IF NOT EXISTS auto_test (id INT)", capturedQueries[0].trim())
        assertEquals("INSERT INTO auto_test VALUES (42)", capturedQueries[1].trim())
        assertEquals("SELECT * FROM auto_test", capturedQueries[2].trim())
        assertEquals("DROP TABLE auto_test", capturedQueries[3].trim())
    }
}
