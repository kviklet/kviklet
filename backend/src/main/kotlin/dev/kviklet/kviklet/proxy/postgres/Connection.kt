// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.proxy.postgres.messages.BindMessage
import dev.kviklet.kviklet.proxy.postgres.messages.CloseMessage
import dev.kviklet.kviklet.proxy.postgres.messages.ExecuteMessage
import dev.kviklet.kviklet.proxy.postgres.messages.MessageFramer
import dev.kviklet.kviklet.proxy.postgres.messages.MessageOrBytes
import dev.kviklet.kviklet.proxy.postgres.messages.ParseMessage
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

class Connection(
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
    private val clientFramer = MessageFramer()
    private val preparedStatements: MutableMap<String, Statement> = mutableMapOf()
    private val portals: MutableMap<String, Portal> = mutableMapOf()
    private var terminationMessageReceived: Boolean = false
    private var serverTerminating: Boolean = false
    private var sessionAborted: Boolean = false

    companion object {
        private val logger = LoggerFactory.getLogger(Connection::class.java)
    }

    // Signals the relay loop to stop. The loop's short read timeout means it notices this within a few
    // milliseconds and then closes both sockets through its single teardown path.
    fun close() {
        this.serverTerminating = true
    }

    // Closes both sockets. Closing the upstream socket is what actually frees the target database
    // connection, and closing the client socket unblocks a peer still waiting on it. Idempotent, so it
    // is safe to call from the relay loop's teardown and again from a concurrent shutdown.
    private fun closeSockets() {
        runCatching { if (!clientSocket.isClosed) clientSocket.close() }
        runCatching { if (!targetSocket.isClosed) targetSocket.close() }
    }

    fun startHandling() {
        // This basically transfers messages between the client and the server sockets.
        // NOTE: At this point the client connection is set up. SSL, Auth etc... are handled in ClientConnectionSetup.kt
        // The finally is the single teardown path for every exit: clean Terminate, client/server EOF,
        // an aborted session, a parse failure, or an exception. Without it the upstream connection and
        // both sockets leak for the lifetime of the proxy (KVI-230).
        try {
            while (!terminationMessageReceived && !serverTerminating && !sessionAborted) {
                val clientOpen = readFromAnyStream(clientInput) { handleClientData(it) }
                val serverOpen = readFromAnyStream(serverInput) { clientOutput.writeAndFlush(it) }
                if (!clientOpen || !serverOpen) {
                    logger.info(
                        "The {} closed the connection, ending the session",
                        if (clientOpen) "server" else "client",
                    )
                    break
                }
            }
        } finally {
            closeSockets()
        }
    }

    private fun handleClientData(clientBuffer: ByteArray) {
        // Fail closed: nothing is forwarded to the server unless the whole chunk was parsed,
        // and each message is audited immediately before it is forwarded, so the audit log
        // only ever contains queries that were at least attempted.
        val messages = try {
            clientFramer.feed(clientBuffer).map { MessageOrBytes(it, null) }
        } catch (e: Exception) {
            logger.warn("Failed to parse client message, blocking it and closing the session", e)
            abortSession(
                "Kviklet proxy could not parse the client message. The message was blocked and the session closed.",
            )
            return
        }
        for (messageOrBytes in messages) {
            if (messageOrBytes.message?.header == 'F') {
                logger.warn("Client sent a fast-path FunctionCall message, blocking it and closing the session")
                abortSession(
                    "Kviklet proxy does not support fast-path function calls because they bypass the audit log. " +
                        "The call was blocked and the session closed.",
                )
                return
            }
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
// Returns false once the stream has reached EOF and no more data will ever arrive
fun readFromAnyStream(input: InputStream, onInputAvailable: (input: ByteArray) -> Unit): Boolean {
    val singleByte = ByteArray(1)
    val bytesRead: Int = try {
        input.read(singleByte, 0, 1)
    } catch (e: SocketTimeoutException) {
        return true
    }
    if (bytesRead == -1) {
        return false
    }

    val buff = ByteArray(8192)
    val read = try {
        input.read(buff)
    } catch (e: SocketTimeoutException) {
        // Only the single polled byte was available before the socket timeout kicked in
        0
    }
    if (read == -1) {
        onInputAvailable(singleByte)
        return false
    }
    onInputAvailable(singleByte + buff.copyOfRange(0, read))
    return true
}
