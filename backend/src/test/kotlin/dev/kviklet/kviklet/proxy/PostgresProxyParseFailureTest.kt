// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.mocks.EventServiceMock
import dev.kviklet.kviklet.proxy.postgres.Connection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import kotlin.concurrent.thread

@SpringBootTest
@ActiveProfiles("test")
class PostgresProxyParseFailureTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter

    @Test
    fun `unparseable client messages are not forwarded, produce an error and are noted in the events`() {
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest()
        val eventService = EventServiceMock(executionRequestAdapter, eventAdapter, request)

        val upstreamListener = ServerSocket(0)
        val clientListener = ServerSocket(0)
        val clientSide = Socket("localhost", clientListener.localPort)
        val proxyClientSocket = clientListener.accept()
        val proxyTargetSocket = Socket("localhost", upstreamListener.localPort)
        val upstreamSide = upstreamListener.accept()
        // Same socket configuration the proxy applies after connection setup
        proxyClientSocket.soTimeout = 10
        proxyTargetSocket.soTimeout = 10

        try {
            val connection = Connection(proxyClientSocket, proxyTargetSocket, eventService, request, "mock")
            val handler = thread { connection.startHandling() }

            // A 'Q' message that declares a 500 byte body but delivers only a fraction of it
            val queryBytes = "DROP TABLE secret".toByteArray()
            val message = ByteBuffer.allocate(5 + queryBytes.size)
            message.put('Q'.code.toByte())
            message.putInt(500)
            message.put(queryBytes)
            clientSide.getOutputStream().write(message.array())
            clientSide.getOutputStream().flush()

            // The client must receive an ErrorResponse ('E'), not a dead socket
            clientSide.soTimeout = 5000
            val firstByte = clientSide.getInputStream().read()
            assertEquals('E'.code, firstByte)

            // The session must terminate
            handler.join(5000)
            assertFalse(handler.isAlive)

            // Nothing must have been forwarded to the target database
            upstreamSide.soTimeout = 500
            val forwarded = try {
                upstreamSide.getInputStream().read()
            } catch (e: SocketTimeoutException) {
                -1
            }
            assertEquals(-1, forwarded)

            // The blocked message must not produce an audit event, only executed queries are audited
            assertTrue(eventService.queries.isEmpty())
        } finally {
            clientSide.close()
            proxyClientSocket.close()
            proxyTargetSocket.close()
            upstreamSide.close()
            upstreamListener.close()
            clientListener.close()
        }
    }
}
