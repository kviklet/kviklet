package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import javax.net.ssl.*


class Connection(
    private var clientSocket: Socket,
    private var targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
) {
    private var clientInput: InputStream = clientSocket.getInputStream()
    private var clientOutput: OutputStream = clientSocket.getOutputStream()
    private var targetInput: InputStream = targetSocket.getInputStream()
    private var targetOutput: OutputStream = targetSocket.getOutputStream()
    private val boundStatements: MutableMap<String, Statement> = mutableMapOf()

    fun startHandling() {

        this.clientInput = clientSocket.getInputStream()
        this.clientOutput = clientSocket.getOutputStream()
        this.targetInput = targetSocket.getInputStream()
        this.targetOutput = targetSocket.getOutputStream()

        this.startMessageExchange()
    }

    private fun startMessageExchange() {
        // This basically transfers messages between the client and the server sockets.
        // Because SSLSocket available method always return zero, the code counts on short read timeout hack
        // Originally this was the case only for the server connections, now it is the case for both the client and the server
        // More info about the hack: https://stackoverflow.com/a/29386157
        // NOTE: At this point the client connection is set up. SSL, Auth etc... are handled in ConnectionSetup.kt
        var lastClientMessage: MessageOrBytes? = null
        while (true) {
            val (clientData, bytesReadFromClient) = fetchClientData()
            if(bytesReadFromClient > 0) {
                val buff = ByteArray(8192)
                val read = clientInput.read(buff)
                val final = ByteArray(read+1)
                System.arraycopy(clientData, 0, final, 0, 1)
                System.arraycopy(buff, 0, final, 1, read)
                lastClientMessage = handleClientData(final, read+1, lastClientMessage)
            }
            val (serverData, bytesReadFromServer) = fetchServerData()
            if (bytesReadFromServer > 0) {
                // Data is available, prepend the read byte to the buffer
                val availableBytes = targetInput.available()
                var dataBuffer = ByteArray(availableBytes + 1)
                System.arraycopy(serverData, 0, dataBuffer, 0, 1)
                if (availableBytes > 0) {
                    targetInput.read(dataBuffer, 1, availableBytes)
                }

                // Handle the data as before
                val responseData = parseResponse(dataBuffer)
                if (responseData.second == "SSL") { // intercept TLS
                    dataBuffer = "N".toByteArray()
                }
                clientOutput.write(dataBuffer, 0, dataBuffer.size)
                clientOutput.flush()
            }
            if (lastClientMessage?.message?.isTermination() == true) {
                break // NOTE: The socket cleanup is handled in the caller
            }
        }
    }

    private fun handleClientData(clientBuffer: ByteArray, size: Int, lastClientMessage: MessageOrBytes?): MessageOrBytes? {

        val data = clientBuffer.copyOf(size)
        var currentLastMessage: MessageOrBytes? = lastClientMessage

        for (clientMessage in parseDataToMessages(data)) {
            val newData = clientMessage.message?.toByteArray() ?: clientMessage.bytes!!
            targetOutput.write(newData, 0, newData.size)
            targetOutput.flush()
            currentLastMessage = clientMessage

        }
        return currentLastMessage
    }

    private fun fetchServerData(): Pair<ByteArray, Int> {
        val singleByte = ByteArray(1)
        val bytesRead: Int = try {
            targetInput.read(singleByte, 0, 1)
        } catch (e: SocketTimeoutException) {
            0
        }
        return Pair(singleByte, bytesRead)
    }
    private fun fetchClientData(): Pair<ByteArray, Int> {
        val singleByte = ByteArray(1)
        val bytesRead: Int = try {
            clientInput.read(singleByte, 0, 1)
        } catch (e: SocketTimeoutException) {
            0
        }
        return Pair(singleByte, bytesRead)
    }

    private fun parseDataToMessages(byteArray: ByteArray): List<MessageOrBytes> {
        val buffer = ByteBuffer.wrap(byteArray)
        val messages = mutableListOf<MessageOrBytes>()
        while (buffer.remaining() > 0) {
            val parsedMessage = ParsedMessage.fromBytes(buffer)
            when (parsedMessage) {
                is ParseMessage -> handleParseMassage(parsedMessage)
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
    private fun handleParseMassage(parsedMessage: ParseMessage) {
        boundStatements[parsedMessage.statementName] = Statement(
            parsedMessage.query,
            parameterTypes = parsedMessage.parameterTypes
        )
    }
    private fun handleBindMessage(parsedMessage: BindMessage) {
        val statement = boundStatements[parsedMessage.statementName]!!
        boundStatements[parsedMessage.statementName] = Statement(
            statement.query,
            parsedMessage.parameterFormatCodes,
            statement.parameterTypes,
            parsedMessage.parameters
        )
    }
    private fun parseResponse(byteArray: ByteArray): Pair<ByteArray, String> = byteArray to "Not handled"
}