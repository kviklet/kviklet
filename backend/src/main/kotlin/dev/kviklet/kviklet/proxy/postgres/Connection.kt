// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.proxy.core.ProxyConnection
import dev.kviklet.kviklet.proxy.core.writeAndFlush
import dev.kviklet.kviklet.proxy.postgres.messages.BindMessage
import dev.kviklet.kviklet.proxy.postgres.messages.CloseMessage
import dev.kviklet.kviklet.proxy.postgres.messages.ExecuteMessage
import dev.kviklet.kviklet.proxy.postgres.messages.MessageFramer
import dev.kviklet.kviklet.proxy.postgres.messages.ParseMessage
import dev.kviklet.kviklet.proxy.postgres.messages.ParsedMessage
import dev.kviklet.kviklet.proxy.postgres.messages.QueryMessage
import dev.kviklet.kviklet.proxy.postgres.messages.Statement
import dev.kviklet.kviklet.proxy.postgres.messages.errorResponse
import dev.kviklet.kviklet.proxy.postgres.messages.readyForQuery
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class Connection(
    private val clientSocket: Socket,
    private val targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
    // The raw accepted TCP socket underneath clientSocket. For a TLS client, clientSocket is an
    // SSLSocket layered over this one with autoClose=false, so closing the SSLSocket leaves this fd (and
    // the parked blocking read on it) open. Closing the underlying socket is what actually unblocks the
    // relay threads and frees the fd, so teardown closes this rather than the SSL wrapper. Defaults to
    // clientSocket for the plain (non-TLS) case, where they are the same socket.
    private val rawClientSocket: Socket = clientSocket,
) : ProxyConnection {
    private var clientInput: InputStream = clientSocket.getInputStream()
    private var clientOutput: OutputStream = clientSocket.getOutputStream()
    private var serverInput: InputStream = targetSocket.getInputStream()
    private var serverOutput: OutputStream = targetSocket.getOutputStream()

    // clientOutput has two writers once the relay runs on two threads: the server->client pump (normal
    // server bytes) and the client->server thread's abortSession (an injected ErrorResponse pair).
    // The lock serializes them so one write cannot be split by the other, and -- because both the pump's
    // forward and abortSession's write consult sessionAborted while holding it -- guarantees no server
    // chunk is forwarded after the abort pair. It does NOT align the abort pair to a server-message
    // boundary: the proxy does not frame the server->client stream, so a pump chunk (a raw read) can end
    // mid-message and the abort pair then follows a partial message. That is harmless here because the
    // session is being torn down; the client errors out either way. serverOutput has a single writer (the
    // client->server thread) so it needs no lock.
    private val clientWriteLock = Any()
    private val clientFramer = MessageFramer()
    private val preparedStatements: MutableMap<String, Statement> = mutableMapOf()
    private val portals: MutableMap<String, Portal> = mutableMapOf()
    private var terminationMessageReceived: Boolean = false

    // Written by close() on the shutdown thread and read by both relay threads, so it needs a
    // happens-before edge. terminationMessageReceived is only touched on the client->server thread.
    @Volatile
    private var serverTerminating: Boolean = false

    // Set by abortSession on the client->server thread and read by the pump thread; every access happens
    // under clientWriteLock, which supplies the happens-before edge (so no @Volatile is needed), and also
    // makes "send the final error pair" and "stop forwarding server bytes" one atomic decision.
    private var sessionAborted: Boolean = false

    companion object {
        private val logger = LoggerFactory.getLogger(Connection::class.java)
    }

    // Signals the relay loop to stop and closes both sockets. The flag gives a clean exit when the loop
    // is between reads, and closing the sockets forces an exit when it is blocked in I/O the read
    // timeout does not bound: a write to a client that has stopped reading would otherwise never reach
    // the flag check and would survive shutdown. closeSockets() is idempotent, so the loop's own
    // teardown running afterwards is harmless.
    override fun close() {
        this.serverTerminating = true
        closeSockets()
    }

    // Closes both sockets. Closing the upstream socket is what actually frees the target database
    // connection, and closing the client socket unblocks a peer still waiting on it. Idempotent, so it
    // is safe to call from the relay loop's teardown and again from a concurrent shutdown.
    //
    // On the client side this closes the raw underlying TCP socket, not the SSLSocket wrapper. For a TLS
    // client the wrapper is created with autoClose=false, so closing it would leave the underlying fd and
    // its parked blocking read open (a leaked thread, fd and session per TLS access window). Closing the
    // underlying socket reliably unblocks both the blocking read and a blocking SSL write; it also avoids
    // SSLSocket.close(), which can block on the TLS output-record lock the pump holds during a write and
    // would otherwise wedge shutdownServer() under the proxy monitor.
    private fun closeSockets() {
        runCatching { if (!rawClientSocket.isClosed) rawClientSocket.close() }
        runCatching { if (!targetSocket.isClosed) targetSocket.close() }
    }

    override fun startHandling() {
        // Two blocking threads per session. This (pool) thread drives client->server: it frames, audits
        // and forwards, and it owns all the audit state (framer, prepared statements, portals) so none of
        // that needs synchronization. A single spawned thread pumps server->client raw.
        // NOTE: At this point the client connection is set up. SSL, Auth etc... are handled in ClientConnectionSetup.kt
        // The finally is the single teardown path for every exit: clean Terminate, client/server EOF,
        // an aborted session, a parse failure, or an exception. It closes both sockets (freeing the
        // upstream connection) which also unblocks the pump thread, then joins it.
        val serverToClient = Thread({ pumpServerToClient() }, "pg-proxy-server-to-client")
        serverToClient.start()
        try {
            while (!terminationMessageReceived && !serverTerminating && !sessionAborted) {
                val chunk = try {
                    readChunk(clientInput)
                } catch (e: Exception) {
                    // A read failure during teardown (the socket was closed under us) is expected; only
                    // an unexpected one is worth surfacing.
                    if (serverTerminating || sessionAborted) break
                    throw e
                } ?: break // client reached EOF
                handleClientData(chunk)
            }
        } finally {
            close()
            serverToClient.join()
        }
    }

    // Raw server->client relay. No parsing or auditing happens on server responses, so this is a dumb
    // byte pump. Writes go through clientWriteLock so they cannot interleave with an abort ErrorResponse.
    private fun pumpServerToClient() {
        try {
            while (!serverTerminating) {
                val chunk = readChunk(serverInput) ?: break // server reached EOF
                synchronized(clientWriteLock) {
                    // If an abort sent the final error pair while this chunk was being read, stop rather
                    // than forwarding server bytes after the ReadyForQuery the client already saw.
                    if (sessionAborted) break
                    clientOutput.writeAndFlush(chunk)
                }
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

    private fun handleClientData(clientBuffer: ByteArray) {
        // Fail closed: nothing is forwarded to the server unless the whole chunk was parsed,
        // and each message is audited immediately before it is forwarded, so the audit log
        // only ever contains queries that were at least attempted.
        val messages = try {
            clientFramer.feed(clientBuffer)
        } catch (e: Exception) {
            logger.warn("Failed to parse client message, blocking it and closing the session", e)
            abortSession(
                "Kviklet proxy could not parse the client message. The message was blocked and the session closed.",
            )
            return
        }
        for (message in messages) {
            if (message.header == 'F') {
                logger.warn("Client sent a fast-path FunctionCall message, blocking it and closing the session")
                abortSession(
                    "Kviklet proxy does not support fast-path function calls because they bypass the audit log. " +
                        "The call was blocked and the session closed.",
                )
                return
            }
            try {
                auditMessage(message)
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
            terminationMessageReceived = terminationMessageReceived || message.isTermination()
            serverOutput.writeAndFlush(message.toByteArray())
        }
    }

    private fun auditMessage(message: ParsedMessage) {
        when (message) {
            is QueryMessage -> handleQuery(message)
            is ParseMessage -> handleParseMessage(message)
            is BindMessage -> handleBindMessage(message)
            is ExecuteMessage -> handleExecute(message)
            is CloseMessage -> handleClose(message)
            else -> {}
        }
    }

    private fun abortSession(reason: String) {
        // Hold the lock across both writes and the flag so a server->client chunk cannot split the
        // ErrorResponse/ReadyForQuery pair, and so the pump (which re-checks sessionAborted under the same
        // lock) forwards nothing after it. Setting the flag inside the lock is what makes the two atomic.
        // Edge case left as-is because the session is dying anyway: if the pump is blocked writing to a
        // client that has stopped reading, it holds the lock and this parks until teardown closes the
        // socket. Bounded by the access window; only unbounded for a never-expiring session.
        synchronized(clientWriteLock) {
            try {
                clientOutput.writeAndFlush(errorResponse(reason))
                clientOutput.writeAndFlush(readyForQuery())
            } catch (e: Exception) {
                logger.warn("Failed to send error response to the client while aborting the session", e)
            }
            sessionAborted = true
        }
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
