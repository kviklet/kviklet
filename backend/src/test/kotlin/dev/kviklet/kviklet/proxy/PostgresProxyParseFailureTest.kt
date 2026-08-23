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

    // A relay session over raw sockets, bypassing connection setup, so tests can speak the wire
    // protocol directly with the same socket configuration the proxy applies after setup
    private class RelayHarness(executionRequestAdapter: ExecutionRequestAdapter, eventAdapter: EventAdapter) :
        AutoCloseable {
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest()
        val eventService = EventServiceMock(executionRequestAdapter, eventAdapter, request)
        private val upstreamListener = ServerSocket(0)
        private val clientListener = ServerSocket(0)
        val clientSide = Socket("localhost", clientListener.localPort)
        private val proxyClientSocket = clientListener.accept()
        private val proxyTargetSocket = Socket("localhost", upstreamListener.localPort)
        val upstreamSide: Socket = upstreamListener.accept()
        val handler: Thread

        init {
            proxyClientSocket.soTimeout = 10
            proxyTargetSocket.soTimeout = 10
            val connection = Connection(proxyClientSocket, proxyTargetSocket, eventService, request, "mock")
            handler = thread { connection.startHandling() }
        }

        fun sendToProxy(bytes: ByteArray) {
            clientSide.getOutputStream().write(bytes)
            clientSide.getOutputStream().flush()
        }

        // Returns -1 if nothing arrives at the target database within the timeout
        fun readByteForwardedUpstream(timeoutMillis: Int = 500): Int {
            upstreamSide.soTimeout = timeoutMillis
            return try {
                upstreamSide.getInputStream().read()
            } catch (e: SocketTimeoutException) {
                -1
            }
        }

        override fun close() {
            clientSide.close()
            proxyClientSocket.close()
            proxyTargetSocket.close()
            upstreamSide.close()
            upstreamListener.close()
            clientListener.close()
        }
    }

    @Test
    fun `unparseable client messages are not forwarded, produce an error and are noted in the events`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            // A message declaring a length below the protocol minimum of 4 can only be garbage
            val message = ByteBuffer.allocate(5 + 4)
            message.put('Q'.code.toByte())
            message.putInt(2)
            message.put("DROP".toByteArray())
            harness.sendToProxy(message.array())

            // The client must receive an ErrorResponse ('E'), not a dead socket
            harness.clientSide.soTimeout = 5000
            val firstByte = harness.clientSide.getInputStream().read()
            assertEquals('E'.code, firstByte)

            // The session must terminate
            harness.handler.join(5000)
            assertFalse(harness.handler.isAlive)

            // Nothing must have been forwarded to the target database
            assertEquals(-1, harness.readByteForwardedUpstream())

            // The blocked message must not produce an audit event, only executed queries are audited
            assertTrue(harness.eventService.queries.isEmpty())
        }
    }

    @Test
    fun `a partially delivered message is buffered and only forwarded once it is complete`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            val queryBytes = "SELECT 1;".toByteArray() + byteArrayOf(0)
            val message = ByteBuffer.allocate(5 + queryBytes.size)
            message.put('Q'.code.toByte())
            message.putInt(4 + queryBytes.size)
            message.put(queryBytes)
            val bytes = message.array()

            // A message arriving in pieces is normal TCP behavior, not a protocol violation:
            // nothing may reach the server (or the audit log) until the message is complete
            harness.sendToProxy(bytes.copyOfRange(0, 5))
            assertEquals(-1, harness.readByteForwardedUpstream())
            assertTrue(harness.eventService.queries.isEmpty())

            harness.sendToProxy(bytes.copyOfRange(5, bytes.size))
            val forwarded = ByteArray(bytes.size)
            harness.upstreamSide.soTimeout = 5000
            var readTotal = 0
            while (readTotal < bytes.size) {
                val read = harness.upstreamSide.getInputStream().read(forwarded, readTotal, bytes.size - readTotal)
                assertTrue(read > 0, "The completed message never arrived at the target database")
                readTotal += read
            }
            assertTrue(bytes.contentEquals(forwarded))
            harness.eventService.assertQueryIsAudited("SELECT 1;")

            // A client disconnect must end the session even without a Terminate message
            harness.clientSide.close()
            harness.handler.join(5000)
            assertFalse(harness.handler.isAlive)
        }
    }
}
