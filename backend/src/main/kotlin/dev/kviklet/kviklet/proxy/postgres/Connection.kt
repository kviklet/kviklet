// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.proxy.postgres.messages.BindMessage
import dev.kviklet.kviklet.proxy.postgres.messages.CloseMessage
import dev.kviklet.kviklet.proxy.postgres.messages.ExecuteMessage
import dev.kviklet.kviklet.proxy.postgres.messages.MessageOrBytes
import dev.kviklet.kviklet.proxy.postgres.messages.ParseMessage
import dev.kviklet.kviklet.proxy.postgres.messages.ParsedMessage
import dev.kviklet.kviklet.proxy.postgres.messages.QueryMessage
import dev.kviklet.kviklet.proxy.postgres.messages.Statement
import dev.kviklet.kviklet.proxy.postgres.messages.errorResponse
import dev.kviklet.kviklet.proxy.postgres.messages.isTermination
import dev.kviklet.kviklet.proxy.postgres.messages.readyForQuery
import dev.kviklet.kviklet.proxy.postgres.messages.writableBytes
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.slf4j.LoggerFactory
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
    private val preparedStatements: MutableMap<String, Statement> = mutableMapOf()
    private val portals: MutableMap<String, Portal> = mutableMapOf()
    private var terminationMessageReceived: Boolean = false
    private var serverTerminating: Boolean = false
    private var sessionAborted: Boolean = false

    companion object {
        private val logger = LoggerFactory.getLogger(Connection::class.java)
    }

    fun close() {
        this.serverTerminating = true
    }
    fun startHandling() {
        // This basically transfers messages between the client and the server sockets.
        // NOTE: At this point the client connection is set up. SSL, Auth etc... are handled in ClientConnectionSetup.kt
        while (!terminationMessageReceived && !serverTerminating && !sessionAborted) {
            readFromAnyStream(clientInput) { handleClientData(it) }
            readFromAnyStream(serverInput) { clientOutput.writeAndFlush(it) }
        }
    }

    private fun handleClientData(clientBuffer: ByteArray) {
        // Fail closed: nothing is forwarded to the server unless the whole chunk was parsed,
        // and each message is audited immediately before it is forwarded, so the audit log
        // only ever contains queries that were at least attempted.
        val messages = try {
            parseDataToMessages(clientBuffer)
        } catch (e: Exception) {
            logger.warn("Failed to parse client message, blocking it and closing the session", e)
            abortSession(
                "Kviklet proxy could not parse the client message. The message was blocked and the session closed.",
            )
            return
        }
        for (messageOrBytes in messages) {
            try {
                auditMessage(messageOrBytes)
            } catch (e: UnknownStatementException) {
                logger.warn("Client referenced a statement or portal unknown to the proxy, closing the session", e)
                abortSession(
                    "Kviklet proxy does not know the statement or portal '${e.name}' and cannot audit " +
                        "this execution. The query was blocked and the session closed.",
                )
                return
            } catch (e: Exception) {
                logger.error("Failed to audit query, blocking it and closing the session", e)
                abortSession(
                    "Kviklet could not record the query in the audit log. The query was blocked and the session closed.",
                )
                return
            }
            terminationMessageReceived = terminationMessageReceived || messageOrBytes.isTermination()
            serverOutput.writeAndFlush(messageOrBytes.writableBytes())
        }
    }

    private fun parseDataToMessages(byteArray: ByteArray): List<MessageOrBytes> {
        val buffer = ByteBuffer.wrap(byteArray)
        val messages = mutableListOf<MessageOrBytes>()
        while (buffer.remaining() > 0) {
            messages.add(MessageOrBytes(ParsedMessage.fromBytes(buffer), null))
        }
        return messages
    }

    private fun auditMessage(messageOrBytes: MessageOrBytes) {
        when (val message = messageOrBytes.message) {
            is QueryMessage -> handleQuery(message)
            is ParseMessage -> handleParseMessage(message)
            is BindMessage -> handleBindMessage(message)
            is ExecuteMessage -> handleExecute(message)
            is CloseMessage -> handleClose(message)
            else -> {}
        }
    }

    private fun abortSession(reason: String) {
        try {
            clientOutput.writeAndFlush(errorResponse(reason))
            clientOutput.writeAndFlush(readyForQuery())
        } catch (e: Exception) {
            logger.warn("Failed to send error response to the client while aborting the session", e)
        }
        sessionAborted = true
    }

    private fun handleQuery(parsedMessage: QueryMessage) {
        val executePayload = ExecutePayload(query = parsedMessage.query)
        eventService.saveEvent(executionRequest.id!!, userId, executePayload)
    }

    private fun handleExecute(parsedMessage: ExecuteMessage) {
        val portal = portals[parsedMessage.portalName]
            ?: throw UnknownStatementException(parsedMessage.portalName)
        // A portal is executed repeatedly when the client pages through results (fetchSize),
        // audit the query once per Bind rather than once per fetched batch
        if (portal.audited) {
            return
        }
        val executePayload = ExecutePayload(query = portal.statement.interpolateQuery())
        eventService.saveEvent(executionRequest.id!!, userId, executePayload)
        portal.audited = true
    }

    private fun handleParseMessage(parsedMessage: ParseMessage) {
        preparedStatements[parsedMessage.statementName] = Statement(
            parsedMessage.query,
            parameterTypes = parsedMessage.parameterTypes,
        )
    }

    private fun handleBindMessage(parsedMessage: BindMessage) {
        val statement = preparedStatements[parsedMessage.statementName]
            ?: throw UnknownStatementException(parsedMessage.statementName)
        portals[parsedMessage.portalName] = Portal(
            Statement(
                statement.query,
                parsedMessage.parameterFormatCodes,
                statement.parameterTypes,
                parsedMessage.parameters,
            ),
        )
    }

    private fun handleClose(parsedMessage: CloseMessage) {
        when (parsedMessage.closeType) {
            'S' -> preparedStatements.remove(parsedMessage.name)
            'P' -> portals.remove(parsedMessage.name)
        }
    }
}

private class Portal(val statement: Statement) {
    var audited: Boolean = false
}

private class UnknownStatementException(val name: String) :
    Exception("Client referenced the statement or portal '$name' which the proxy has not seen before")

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
