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

    private val parser = MySqlClientPacketParser { query ->
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
        while (!terminationMessageReceived && !serverTerminating) {
            readFromAnyStream(clientInput) { handleClientData(it) }
            readFromAnyStream(serverInput) { clientOutput.writeAndFlush(it) }
        }
    }

    private fun handleClientData(clientBuffer: ByteArray) {
        // Feed raw bytes to parser for query auditing
        parser.addBytes(clientBuffer)
        
        // Check for COM_QUIT (sequence id doesn't matter, first payload byte of packet can be 0x01)
        if (clientBuffer.size >= 5) {
            val cmd = clientBuffer[4].toInt() and 0xFF
            if (cmd == 0x01) { // COM_QUIT
                terminationMessageReceived = true
            }
        }
        
        // Forward raw bytes to the server
        serverOutput.writeAndFlush(clientBuffer)
    }
}

class MySqlClientPacketParser(private val onQuery: (String) -> Unit) {
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
                if (cmd == 0x03 || cmd == 0x16) { // COM_QUERY or COM_STMT_PREPARE
                    try {
                        val query = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                        if (query.trim().isNotEmpty()) {
                            onQuery(query)
                        }
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

fun readFromAnyStream(input: InputStream, onInputAvailable: (input: ByteArray) -> Unit) {
    val singleByte = ByteArray(1)
    val bytesRead: Int = try {
        input.read(singleByte, 0, 1)
    } catch (e: SocketTimeoutException) {
        0
    }

    if (bytesRead > 0) {
        val buff = ByteArray(8192)
        val read = input.read(buff)
        onInputAvailable(singleByte + buff.copyOfRange(0, read))
    }
}

fun OutputStream.writeAndFlush(b: ByteArray) {
    this.write(b)
    this.flush()
}
