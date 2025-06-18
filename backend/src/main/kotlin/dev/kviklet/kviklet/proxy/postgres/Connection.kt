package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.proxy.postgres.messages.*
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer


class Connection(
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
    private val boundStatements: MutableMap<String, Statement> = mutableMapOf()
    private var terminationMessageReceived: Boolean = false
    private var serverTerminating : Boolean = false

     fun close() {
        this.serverTerminating = true

    }
    fun startHandling() {
        // This basically transfers messages between the client and the server sockets.
        // NOTE: At this point the client connection is set up. SSL, Auth etc... are handled in ClientConnectionSetup.kt
        while (!terminationMessageReceived && !serverTerminating) {
            readFromAnyStream(clientInput) { handleClientData(it) }
            readFromAnyStream(serverInput) { clientOutput.writeAndFlush(it) }
        }
    }

    private fun handleClientData(clientBuffer: ByteArray) {
        for (messageOrBytes in parseDataToMessages(clientBuffer.copyOf(clientBuffer.size))) {
            terminationMessageReceived = messageOrBytes.isTermination()
            serverOutput.writeAndFlush(messageOrBytes.writableBytes())
        }
    }

    private fun parseDataToMessages(byteArray: ByteArray): List<MessageOrBytes> {
        val buffer = ByteBuffer.wrap(byteArray)
        val messages = mutableListOf<MessageOrBytes>()
        while (buffer.remaining() > 0) {
            val parsedMessage = ParsedMessage.fromBytes(buffer)
            when (parsedMessage) {
                is ParseMessage -> handleParseMessage(parsedMessage)
                is BindMessage -> handleBindMessage(parsedMessage)
                is ExecuteMessage -> handleExecute(parsedMessage)
            }
            messages.add(MessageOrBytes(parsedMessage, null))
        }
        return messages
    }

    private fun handleExecute(parsedMessage: ExecuteMessage) {
        val statement = boundStatements[parsedMessage.statementName]!!
        val executePayload = ExecutePayload(query = statement.interpolateQuery())
        eventService.saveEvent(executionRequest.id!!, userId, executePayload)
    }

    private fun handleParseMessage(parsedMessage: ParseMessage) {
        boundStatements[parsedMessage.statementName] = Statement(
            parsedMessage.query,
            parameterTypes = parsedMessage.parameterTypes
        )
    }

    private fun handleBindMessage(parsedMessage: BindMessage) {
        val statement = boundStatements[parsedMessage.statementName]!!
        boundStatements[parsedMessage.statementName] =
            Statement(
                statement.query,
                parsedMessage.parameterFormatCodes,
                statement.parameterTypes,
                parsedMessage.parameters
            )
    }
}

// Because SSLSocket available method always return zero, the code counts on short read timeout hack
// Originally this was the case only for the server connections, now it is the case for both the client and the server
// More info about the hack: https://stackoverflow.com/a/29386157
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