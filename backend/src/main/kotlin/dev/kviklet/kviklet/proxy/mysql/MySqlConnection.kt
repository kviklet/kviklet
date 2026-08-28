// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.proxy.core.ProxyConnection
import dev.kviklet.kviklet.proxy.core.writeAndFlush
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

private val logger = LoggerFactory.getLogger("MySqlConnection")

class MySqlConnection(
    private val clientSocket: Socket,
    private val targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
    // The raw accepted TCP socket underneath clientSocket. For a TLS client, clientSocket is an SSLSocket
    // layered over this one with autoClose=false, so closing the SSLSocket would leave this fd (and the
    // parked blocking read on it) open. Closing the underlying socket is what actually unblocks the relay
    // threads and frees the fd, so teardown closes this rather than the SSL wrapper. Defaults to
    // clientSocket for the plain (non-TLS) case, where they are the same socket.
    private val rawClientSocket: Socket = clientSocket,
    // The JDBC connection targetSocket was extracted from. Held here so it stays strongly referenced for
    // the relay's lifetime (a driver may reap the network resources of a connection object that is
    // garbage-collected without close()) and closed quietly on teardown to release driver bookkeeping.
    private val upstreamJdbcConnection: AutoCloseable? = null,
) : ProxyConnection {
    private var clientInput: InputStream = clientSocket.getInputStream()
    private var clientOutput: OutputStream = clientSocket.getOutputStream()
    private var serverInput: InputStream = targetSocket.getInputStream()
    private var serverOutput: OutputStream = targetSocket.getOutputStream()

    // Only touched on the client->server thread; the loop exits after the COM_QUIT chunk was forwarded.
    private var terminationMessageReceived: Boolean = false

    // Written by close() on the shutdown thread and read by both relay threads, so it needs a
    // happens-before edge.
    @Volatile
    private var serverTerminating: Boolean = false

    @Volatile
    private var awaitingPrepareResponse = false
    private var lastPreparedQuery: String? = null
    private val preparedQueries = ConcurrentHashMap<Int, String>()

    private val clientParser = MySqlClientPacketParser(
        onQuery = { query ->
            auditQuery(query)
        },
        onPrepare = { query ->
            synchronized(this) {
                lastPreparedQuery = query
                awaitingPrepareResponse = true
            }
        },
        onExecute = { stmtId ->
            val query = preparedQueries[stmtId]
            if (query != null) {
                auditQuery(query)
            }
        },
        onClose = { stmtId ->
            // Release the stored query when the client closes the prepared statement
            preparedQueries.remove(stmtId)
        },
        onQuit = {
            terminationMessageReceived = true
        },
    )

    private val serverParser = MySqlServerPacketParser(
        onPrepareOk = { stmtId ->
            synchronized(this) {
                if (awaitingPrepareResponse) {
                    lastPreparedQuery?.let { preparedQueries[stmtId] = it }
                    awaitingPrepareResponse = false
                    lastPreparedQuery = null
                }
            }
        },
        onPrepareErr = {
            // The server rejected the COM_STMT_PREPARE; drop the pending state so the
            // next unrelated OK packet is not mistaken for this prepare's response.
            synchronized(this) {
                awaitingPrepareResponse = false
                lastPreparedQuery = null
            }
        },
    )

    private fun auditQuery(query: String) {
        try {
            val executePayload = ExecutePayload(query = query)
            eventService.saveEvent(executionRequest.id!!, userId, executePayload)
        } catch (e: Exception) {
            logger.error("Failed to audit query", e)
        }
    }

    // Signals the relay loops to stop and closes both sockets. The flag gives a clean exit when a loop is
    // between reads, and closing the sockets forces an exit when it is parked in blocking I/O. Idempotent:
    // safe to call from either relay thread's teardown and again from a concurrent session expiry or
    // server shutdown.
    override fun close() {
        serverTerminating = true
        closeSockets()
    }

    // Closes both sockets. Closing the upstream socket is what actually frees the target database
    // connection, and closing the client socket unblocks a peer still waiting on it. On the client side
    // this closes the raw underlying TCP socket, not the SSLSocket wrapper: for a TLS client the wrapper
    // would leave the underlying fd and its parked blocking read open, and SSLSocket.close() can also
    // block on the TLS output-record lock a concurrent write holds (see the Postgres relay).
    private fun closeSockets() {
        runCatching { if (!rawClientSocket.isClosed) rawClientSocket.close() }
        runCatching { if (!targetSocket.isClosed) targetSocket.close() }
        // After the sockets: the driver's own close (a Quit attempt on the already-closed socket) may
        // fail, which is fine -- this is only about releasing its bookkeeping. JDBC close is idempotent.
        upstreamJdbcConnection?.let { runCatching { it.close() } }
    }

    override fun startHandling() {
        // Two blocking threads per session. This (pool) thread drives client->server: it feeds the packet
        // parser (which audits queries and tracks prepared statements) and forwards the raw bytes. A single
        // spawned thread pumps server->client, feeding its parser only to match prepared-statement ids.
        // The finally is the single teardown path for every exit: clean COM_QUIT, client/server EOF or an
        // exception. It closes both sockets (freeing the upstream connection) which also unblocks the pump
        // thread, then joins it.
        val serverToClient = Thread({ pumpServerToClient() }, "mysql-proxy-server-to-client")
        serverToClient.start()
        try {
            while (!terminationMessageReceived && !serverTerminating) {
                val chunk = try {
                    readChunk(clientInput)
                } catch (e: Exception) {
                    // A read failure during teardown (the socket was closed under us) is expected; only an
                    // unexpected one is worth surfacing.
                    if (serverTerminating) break
                    throw e
                } ?: break // client reached EOF
                // Audit before forwarding: the parser callbacks run synchronously inside addBytes, so every
                // query is recorded before its bytes reach the server.
                clientParser.addBytes(chunk)
                serverOutput.writeAndFlush(chunk)
            }
        } finally {
            close()
            serverToClient.join()
        }
    }

    private fun pumpServerToClient() {
        try {
            while (!serverTerminating) {
                val chunk = readChunk(serverInput) ?: break // server reached EOF
                serverParser.addBytes(chunk)
                clientOutput.writeAndFlush(chunk)
            }
        } catch (e: Exception) {
            // The client->server thread closing the sockets during teardown unblocks this read with an
            // exception; that is the normal way this thread ends, so it is not an error.
            logger.info("Server-to-client relay ended: {}", e.message)
        } finally {
            // Closing here unblocks the client->server thread if the server was the side that went away.
            close()
        }
    }

    // Blocking read of one chunk. Returns null at EOF. Throws if the socket is closed mid-read.
    private fun readChunk(input: InputStream): ByteArray? {
        val buff = ByteArray(8192)
        val read = input.read(buff)
        if (read == -1) return null
        return buff.copyOfRange(0, read)
    }
}

class MySqlClientPacketParser(
    private val onQuery: (String) -> Unit,
    private val onPrepare: (String) -> Unit,
    private val onExecute: (Int) -> Unit,
    private val onClose: (Int) -> Unit = {},
    private val onQuit: () -> Unit,
) {
    private val buffer = ByteArrayOutputStream()

    fun addBytes(bytes: ByteArray) {
        synchronized(this) {
            buffer.write(bytes)
            processBuffer()
        }
    }

    private fun processBuffer() {
        val data = buffer.toByteArray()
        var offset = 0
        while (data.size - offset >= 4) {
            val length = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16)

            if (data.size - offset < 4 + length) {
                break // Need more data for a full packet
            }

            val payload = ByteArray(length)
            System.arraycopy(data, offset + 4, payload, 0, length)

            if (payload.isNotEmpty()) {
                val cmd = payload[0].toInt() and 0xFF
                try {
                    when (cmd) {
                        0x01 -> { // COM_QUIT
                            onQuit()
                        }

                        0x03 -> { // COM_QUERY
                            val query = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                            if (query.trim().isNotEmpty()) {
                                onQuery(query)
                            }
                        }

                        0x16 -> { // COM_STMT_PREPARE
                            val query = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                            if (query.trim().isNotEmpty()) {
                                onPrepare(query)
                            }
                        }

                        0x17 -> { // COM_STMT_EXECUTE
                            if (payload.size >= 5) {
                                val stmtId = (payload[1].toInt() and 0xFF) or
                                    ((payload[2].toInt() and 0xFF) shl 8) or
                                    ((payload[3].toInt() and 0xFF) shl 16) or
                                    ((payload[4].toInt() and 0xFF) shl 24)
                                onExecute(stmtId)
                            }
                        }

                        0x19 -> { // COM_STMT_CLOSE
                            if (payload.size >= 5) {
                                val stmtId = (payload[1].toInt() and 0xFF) or
                                    ((payload[2].toInt() and 0xFF) shl 8) or
                                    ((payload[3].toInt() and 0xFF) shl 16) or
                                    ((payload[4].toInt() and 0xFF) shl 24)
                                onClose(stmtId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error parsing client packet", e)
                }
            }

            offset += 4 + length
        }

        buffer.reset()
        if (data.size > offset) {
            buffer.write(data, offset, data.size - offset)
        }
    }
}

class MySqlServerPacketParser(private val onPrepareOk: (Int) -> Unit, private val onPrepareErr: () -> Unit = {}) {
    private val buffer = ByteArrayOutputStream()

    fun addBytes(bytes: ByteArray) {
        synchronized(this) {
            buffer.write(bytes)
            processBuffer()
        }
    }

    private fun processBuffer() {
        val data = buffer.toByteArray()
        var offset = 0
        while (data.size - offset >= 4) {
            val length = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16)

            if (data.size - offset < 4 + length) {
                break // Need more data for a full packet
            }

            val payload = ByteArray(length)
            System.arraycopy(data, offset + 4, payload, 0, length)

            if (payload.isNotEmpty()) {
                val status = payload[0].toInt() and 0xFF
                // COM_STMT_PREPARE_OK has a fixed 12-byte payload (0x00 status + 4 stmt_id
                // + 2 columns + 2 params + 1 reserved + 2 warnings). Requiring the exact
                // length avoids mistaking a generic OK packet (also 0x00) for a prepare-ok.
                // ERR packets (0xFF) clear any pending prepare so a later OK is not misread.
                if (status == 0xFF) {
                    onPrepareErr()
                } else if (status == 0x00 && payload.size == 12) {
                    try {
                        val stmtId = (payload[1].toInt() and 0xFF) or
                            ((payload[2].toInt() and 0xFF) shl 8) or
                            ((payload[3].toInt() and 0xFF) shl 16) or
                            ((payload[4].toInt() and 0xFF) shl 24)
                        onPrepareOk(stmtId)
                    } catch (e: Exception) {
                        logger.error("Error parsing server prepare-ok packet", e)
                    }
                }
            }

            offset += 4 + length
        }

        buffer.reset()
        if (data.size > offset) {
            buffer.write(data, offset, data.size - offset)
        }
    }
}
