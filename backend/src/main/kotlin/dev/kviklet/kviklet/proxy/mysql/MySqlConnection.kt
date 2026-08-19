package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

private val logger = LoggerFactory.getLogger("MySqlConnection")

class MySqlConnection(
    private val clientSocket: Socket,
    private val targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
) {
    private var clientInput: InputStream = clientSocket.getInputStream()
    private var clientOutput: OutputStream = clientSocket.getOutputStream()
    private var serverInput: InputStream = targetSocket.getInputStream()
    private var serverOutput: OutputStream = targetSocket.getOutputStream()

    @Volatile private var terminationMessageReceived: Boolean = false

    @Volatile private var serverTerminating: Boolean = false

    @Volatile
    private var awaitingPrepareResponse = false
    private var lastPreparedQuery: String? = null
    private val preparedQueries = java.util.concurrent.ConcurrentHashMap<Int, String>()

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

    fun close() {
        serverTerminating = true
        // Close both sockets to unblock any pending reads in the forwarding threads
        try {
            clientSocket.close()
        } catch (_: Exception) {}
        try {
            targetSocket.close()
        } catch (_: Exception) {}
    }

    fun startHandling() {
        val clientToServer = Thread {
            try {
                val buf = ByteArray(8192)
                while (!terminationMessageReceived && !serverTerminating) {
                    val n = try {
                        clientInput.read(buf)
                    } catch (_: Exception) {
                        -1
                    }
                    if (n < 0) break
                    if (n > 0) handleClientData(buf.copyOfRange(0, n))
                }
            } catch (e: Exception) {
                logger.error("Error forwarding client-to-server traffic", e)
            } finally {
                close()
            }
        }
        val serverToClient = Thread {
            try {
                val buf = ByteArray(8192)
                while (!serverTerminating) {
                    val n = try {
                        serverInput.read(buf)
                    } catch (_: Exception) {
                        -1
                    }
                    if (n < 0) break
                    if (n > 0) handleServerData(buf.copyOfRange(0, n))
                }
            } catch (e: Exception) {
                logger.error("Error forwarding server-to-client traffic", e)
            } finally {
                close()
            }
        }
        clientToServer.isDaemon = true
        serverToClient.isDaemon = true
        clientToServer.start()
        serverToClient.start()
        clientToServer.join()
        serverToClient.join()
    }

    private fun handleClientData(clientBuffer: ByteArray) {
        // Feed raw bytes to client parser for query/prepare/execute/quit auditing
        clientParser.addBytes(clientBuffer)

        // Forward raw bytes to the server
        serverOutput.writeAndFlush(clientBuffer)
    }

    private fun handleServerData(serverBuffer: ByteArray) {
        // Feed raw bytes to server parser to match generated statement IDs
        serverParser.addBytes(serverBuffer)

        // Forward raw bytes to the client
        clientOutput.writeAndFlush(serverBuffer)
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
        var data = buffer.toByteArray()
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
        var data = buffer.toByteArray()
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

fun OutputStream.writeAndFlush(b: ByteArray) {
    this.write(b)
    this.flush()
}
