// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.EventAdapter
import dev.kviklet.kviklet.db.ExecutionRequestAdapter
import dev.kviklet.kviklet.helper.ExecutionRequestFactory
import dev.kviklet.kviklet.proxy.mocks.FailingEventServiceMock
import dev.kviklet.kviklet.proxy.mysql.MySqlConnection
import dev.kviklet.kviklet.proxy.mysql.readPacket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

@SpringBootTest
@ActiveProfiles("test")
class MySqlProxyFailClosedTest {
    @Autowired
    lateinit var executionRequestAdapter: ExecutionRequestAdapter

    @Autowired
    lateinit var eventAdapter: EventAdapter

    // A relay session over raw sockets, bypassing connection setup, so tests can speak the wire protocol
    // directly with the same socket configuration the proxy applies after authentication
    private class RelayHarness(executionRequestAdapter: ExecutionRequestAdapter, eventAdapter: EventAdapter) :
        AutoCloseable {
        val request = ExecutionRequestFactory().createDatasourceExecutionRequest()
        val eventService = FailingEventServiceMock(executionRequestAdapter, eventAdapter, request)
        private val upstreamListener = ServerSocket(0)
        private val clientListener = ServerSocket(0)
        val clientSide = Socket("localhost", clientListener.localPort)
        private val proxyClientSocket = clientListener.accept()
        private val proxyTargetSocket = Socket("localhost", upstreamListener.localPort)
        val upstreamSide: Socket = upstreamListener.accept()
        val handler: Thread

        init {
            // Match the socket configuration the proxy applies to the relay after setup: the blocking
            // thread-per-direction relay uses no read timeout (soTimeout 0), parking in a read until data
            // arrives or the socket is closed on teardown.
            proxyClientSocket.soTimeout = 0
            proxyTargetSocket.soTimeout = 0
            val connection = MySqlConnection(proxyClientSocket, proxyTargetSocket, eventService, request, "mock")
            handler = thread { connection.startHandling() }
        }

        fun sendToProxy(bytes: ByteArray) {
            clientSide.getOutputStream().write(bytes)
            clientSide.getOutputStream().flush()
        }

        // The next complete packet the proxy forwarded to the target database
        fun readPacketForwardedUpstream(timeoutMillis: Int = 5000): Pair<Int, ByteArray> {
            upstreamSide.soTimeout = timeoutMillis
            return readPacket(upstreamSide.getInputStream())
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

        fun sendFromUpstream(bytes: ByteArray) {
            upstreamSide.getOutputStream().write(bytes)
            upstreamSide.getOutputStream().flush()
        }

        // The next complete packet the proxy relayed back to the client
        fun readPacketOnClient(timeoutMillis: Int = 5000): Pair<Int, ByteArray> {
            clientSide.soTimeout = timeoutMillis
            return readPacket(clientSide.getInputStream())
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
    fun `a query whose audit write fails is blocked with an ERR packet and the session is closed`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            harness.eventService.failing = true
            harness.sendToProxy(comQuery("DROP TABLE users"))

            // The client must receive an ERR packet (0xFF), not a dead socket
            val (_, errPayload) = harness.readPacketOnClient()
            assertEquals(0xFF, errPayload[0].toInt() and 0xFF)

            harness.handler.join(5000)
            assertFalse(harness.handler.isAlive)

            assertEquals(-1, harness.readByteForwardedUpstream())
        }
    }

    @Test
    fun `an execute of a statement id the proxy never saw prepared is blocked`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            harness.sendToProxy(comStmtExecute(42))

            val (_, errPayload) = harness.readPacketOnClient()
            assertEquals(0xFF, errPayload[0].toInt() and 0xFF)

            harness.handler.join(5000)
            assertFalse(harness.handler.isAlive)

            assertEquals(-1, harness.readByteForwardedUpstream())
            assertTrue(harness.eventService.queries.isEmpty())
        }
    }

    @Test
    fun `an executed prepared statement is audited with the text it was prepared with`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            val sql = "INSERT INTO logs (message) VALUES (?)"
            harness.sendToProxy(mysqlPacket(0, byteArrayOf(0x16) + sql.toByteArray(Charsets.UTF_8)))
            harness.readPacketForwardedUpstream()

            // The upstream answers with a prepare-ok assigning statement id 7; reading the relayed
            // response on the client guarantees the proxy has processed it
            harness.sendFromUpstream(prepareOk(sequenceId = 1, stmtId = 7))
            harness.readPacketOnClient()

            harness.sendToProxy(comStmtExecute(7))
            val (_, forwarded) = harness.readPacketForwardedUpstream()
            assertEquals(0x17, forwarded[0].toInt() and 0xFF)
            harness.eventService.assertAuditedQueryContains(sql)
        }
    }

    @Test
    fun `sequential prepares are each attributed to their own statement id`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            val firstSql = "SELECT * FROM users WHERE id = ?"
            val secondSql = "DELETE FROM users WHERE id = ?"

            // Each prepare completes (its prepare-ok is relayed back to the client) before the next is sent,
            // the way a synchronous client drives the connection
            harness.sendToProxy(mysqlPacket(0, byteArrayOf(0x16) + firstSql.toByteArray(Charsets.UTF_8)))
            harness.readPacketForwardedUpstream()
            harness.sendFromUpstream(prepareOk(sequenceId = 1, stmtId = 1))
            harness.readPacketOnClient()

            harness.sendToProxy(mysqlPacket(0, byteArrayOf(0x16) + secondSql.toByteArray(Charsets.UTF_8)))
            harness.readPacketForwardedUpstream()
            harness.sendFromUpstream(prepareOk(sequenceId = 1, stmtId = 2))
            harness.readPacketOnClient()

            harness.sendToProxy(comStmtExecute(1))
            harness.readPacketForwardedUpstream()
            harness.sendToProxy(comStmtExecute(2))
            harness.readPacketForwardedUpstream()

            assertEquals(listOf(firstSql, secondSql), harness.eventService.rawQueries)
        }
    }

    @Test
    fun `a second prepare pipelined before the first response is blocked`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            // Two prepares in one chunk, before either prepare-ok arrives: the proxy cannot pair the
            // responses reliably, so it must fail closed instead of guessing (which could misattribute a
            // statement id to the wrong query text)
            harness.sendToProxy(
                mysqlPacket(0, byteArrayOf(0x16) + "SELECT * FROM users WHERE id = ?".toByteArray(Charsets.UTF_8)) +
                    mysqlPacket(0, byteArrayOf(0x16) + "DELETE FROM users WHERE id = ?".toByteArray(Charsets.UTF_8)),
            )

            val (_, errPayload) = harness.readPacketOnClient()
            assertEquals(0xFF, errPayload[0].toInt() and 0xFF)

            harness.handler.join(5000)
            assertFalse(harness.handler.isAlive)

            // The whole chunk is dropped: neither prepare reaches the server
            assertEquals(-1, harness.readByteForwardedUpstream())
        }
    }

    @Test
    fun `an execute whose parameters cannot be decoded is forwarded and audited with the placeholder text`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            val sql = "INSERT INTO logs (message) VALUES (?)"
            harness.sendToProxy(mysqlPacket(0, byteArrayOf(0x16) + sql.toByteArray(Charsets.UTF_8)))
            harness.readPacketForwardedUpstream()
            harness.sendFromUpstream(prepareOk(sequenceId = 1, stmtId = 7, paramCount = 1))
            harness.readPacketOnClient()

            // The execute carries an unknown parameter type (0x77): the decoder must abandon
            // interpolation, audit the placeholder text and still forward the packet -- never block a
            // legitimate client or splice unverifiable values into the audit log.
            harness.sendToProxy(comStmtExecuteWithParam(7, typeField = 0x77, value = byteArrayOf(1, 0, 0, 0)))
            val (_, forwarded) = harness.readPacketForwardedUpstream()
            assertEquals(0x17, forwarded[0].toInt() and 0xFF)
            harness.eventService.assertAuditedQueryContains(sql)
        }
    }

    @Test
    fun `a long data mark does not outlive its execute`() {
        RelayHarness(executionRequestAdapter, eventAdapter).use { harness ->
            val sql = "INSERT INTO logs (message) VALUES (?)"
            harness.sendToProxy(mysqlPacket(0, byteArrayOf(0x16) + sql.toByteArray(Charsets.UTF_8)))
            harness.readPacketForwardedUpstream()
            harness.sendFromUpstream(prepareOk(sequenceId = 1, stmtId = 7, paramCount = 1))
            harness.readPacketOnClient()

            // First execute: the parameter's bytes were streamed ahead via COM_STMT_SEND_LONG_DATA, so
            // the execute packet has no value for it and the audit shows the explicit marker.
            harness.sendToProxy(mysqlPacket(0, byteArrayOf(0x18, 7, 0, 0, 0, 0, 0) + "streamed".toByteArray()))
            harness.readPacketForwardedUpstream()
            harness.sendToProxy(comStmtExecuteWithParam(7, typeField = 0xFD, value = null))
            harness.readPacketForwardedUpstream()
            harness.eventService.assertAuditedQueryContains("VALUES (<long data>)")

            // Long data is consumed by that execute. A re-execute with the value inline in the packet
            // must be decoded normally -- a leaked mark would make the decoder skip the value and degrade
            // (or worse, misread) the audit entry.
            harness.sendToProxy(
                comStmtExecuteWithParam(7, typeField = 0xFD, value = byteArrayOf(3) + "abc".toByteArray()),
            )
            harness.readPacketForwardedUpstream()
            harness.eventService.assertAuditedQueryContains("VALUES ('abc')")
        }
    }

    private fun comQuery(sql: String): ByteArray = mysqlPacket(0, byteArrayOf(0x03) + sql.toByteArray(Charsets.UTF_8))

    private fun comStmtExecute(stmtId: Int): ByteArray = mysqlPacket(
        0,
        byteArrayOf(
            0x17,
            (stmtId and 0xFF).toByte(),
            ((stmtId ushr 8) and 0xFF).toByte(),
            ((stmtId ushr 16) and 0xFF).toByte(),
            ((stmtId ushr 24) and 0xFF).toByte(),
            0x00, // flags
            0x01, 0x00, 0x00, 0x00, // iteration count
        ),
    )

    // COM_STMT_PREPARE_OK: 0x00 status + 4-byte stmt id + 2 columns + 2 params + 1 reserved + 2 warnings
    private fun prepareOk(sequenceId: Int, stmtId: Int, paramCount: Int = 0): ByteArray {
        val payload = ByteArray(12)
        payload[1] = (stmtId and 0xFF).toByte()
        payload[2] = ((stmtId ushr 8) and 0xFF).toByte()
        payload[3] = ((stmtId ushr 16) and 0xFF).toByte()
        payload[4] = ((stmtId ushr 24) and 0xFF).toByte()
        payload[7] = (paramCount and 0xFF).toByte()
        payload[8] = ((paramCount ushr 8) and 0xFF).toByte()
        return mysqlPacket(sequenceId, payload)
    }

    // A COM_STMT_EXECUTE for one non-NULL parameter with its type resent; a null value means the value
    // bytes are absent from the packet (the way a long-data parameter is sent).
    private fun comStmtExecuteWithParam(stmtId: Int, typeField: Int, value: ByteArray?): ByteArray {
        val payload = ByteArrayOutputStream()
        payload.write(0x17)
        payload.write(stmtId and 0xFF)
        payload.write((stmtId ushr 8) and 0xFF)
        payload.write((stmtId ushr 16) and 0xFF)
        payload.write((stmtId ushr 24) and 0xFF)
        payload.write(0x00) // flags
        payload.write(byteArrayOf(0x01, 0x00, 0x00, 0x00)) // iteration count
        payload.write(0x00) // null bitmap: the one parameter is not NULL
        payload.write(0x01) // new-params-bound flag
        payload.write(typeField and 0xFF)
        payload.write((typeField ushr 8) and 0xFF)
        value?.let { payload.write(it) }
        return mysqlPacket(0, payload.toByteArray())
    }

    private fun mysqlPacket(sequenceId: Int, payload: ByteArray): ByteArray {
        val header = byteArrayOf(
            (payload.size and 0xFF).toByte(),
            ((payload.size ushr 8) and 0xFF).toByte(),
            ((payload.size ushr 16) and 0xFF).toByte(),
            (sequenceId and 0xFF).toByte(),
        )
        return header + payload
    }
}
