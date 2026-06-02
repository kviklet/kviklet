package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException

class MySqlConnection(
    clientSocket: Socket,
    targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
) {
    private var clientInput: InputStream = clientSocket.getInputStream()
    private var clientOutput: OutputStream = clientSocket.getOutputStream()
    private var serverInput: InputStream = targetSocket.getInputStream()
    private var serverOutput: OutputStream = targetSocket.getOutputStream()
    private var terminationMessageReceived: Boolean = false
    private var serverTerminating: Boolean = false

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
        onQuit = {
            terminationMessageReceived = true
        }
    )

    private val serverParser = MySqlServerPacketParser { stmtId ->
        synchronized(this) {
            if (awaitingPrepareResponse) {
                lastPreparedQuery?.let { preparedQueries[stmtId] = it }
                awaitingPrepareResponse = false
                lastPreparedQuery = null
            }
        }
    }

    private fun auditQuery(query: String) {
        try {
            val executePayload = ExecutePayload(query = query)
            eventService.saveEvent(executionRequest.id!!, userId, executePayload)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun close() {
        this.serverTerminating = true
    }

    fun startHandling() {
        try {
            while (!terminationMessageReceived && !serverTerminating) {
                val clientOk = readFromAnyStream(clientInput) { handleClientData(it) }
                if (!clientOk) break
                val serverOk = readFromAnyStream(serverInput) { handleServerData(it) }
                if (!serverOk) break
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            close()
        }
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
    private val onQuit: () -> Unit
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
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
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

class MySqlServerPacketParser(private val onPrepareOk: (Int) -> Unit) {
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
                // STMT_PREPARE_OK starts with 0x00 status and is 12 bytes long
                if (status == 0x00 && payload.size >= 9) {
                    try {
                        val stmtId = (payload[1].toInt() and 0xFF) or
                                     ((payload[2].toInt() and 0xFF) shl 8) or
                                     ((payload[3].toInt() and 0xFF) shl 16) or
                                     ((payload[4].toInt() and 0xFF) shl 24)
                        onPrepareOk(stmtId)
                    } catch (e: Exception) {
                        e.printStackTrace()
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

fun readFromAnyStream(input: InputStream, onInputAvailable: (input: ByteArray) -> Unit): Boolean {
    val singleByte = ByteArray(1)
    val bytesRead: Int = try {
        input.read(singleByte, 0, 1)
    } catch (e: SocketTimeoutException) {
        0
    }

    if (bytesRead < 0) {
        return false // Connection reached EOF / was closed
    }

    if (bytesRead > 0) {
        val buff = ByteArray(8192)
        val read: Int = try {
            input.read(buff)
        } catch (e: SocketTimeoutException) {
            0
        }
        if (read > 0) {
            onInputAvailable(singleByte + buff.copyOfRange(0, read))
        } else {
            onInputAvailable(singleByte)
        }
    }
    return true
}

fun OutputStream.writeAndFlush(b: ByteArray) {
    this.write(b)
    this.flush()
}
