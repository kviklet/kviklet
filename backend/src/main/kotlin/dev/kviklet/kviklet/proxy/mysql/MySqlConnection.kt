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
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

private val logger = LoggerFactory.getLogger("MySqlConnection")

// A payload of exactly 0xFFFFFF signals that the logical payload continues in the next packet, so the
// reassembled payload needs its own bound: like the Postgres proxy's 1GB message cap, anything beyond it
// can only be garbage or an attack, and buffering it would be unbounded. MySQL itself caps
// max_allowed_packet at 1GB.
private const val MAX_SPLIT_PACKET_LENGTH = 0xFFFFFF
private const val MAX_ASSEMBLED_PAYLOAD_LENGTH = 0x40000000

// How long abortSession waits for the client write lock before giving up on the courtesy ERR packet and
// closing the sockets anyway. Kept short: the lock is only ever held for a single relayed write, and the
// close() that follows a timeout is what actually unblocks a write that has parked.
private const val ABORT_ERR_TIMEOUT_MS = 2000L

// A fail-closed violation on the relay: traffic the proxy must not forward because the audit log could not
// record it (a failed audit write, an execute of a statement the proxy cannot attribute, a command that
// sidesteps the audited session). The message is client-facing: it is sent to the client in the ERR packet
// that aborts the session.
class FailClosedException(message: String, cause: Throwable? = null) : Exception(message, cause)

// Everything the proxy tracks about one live server-side prepared statement. query and paramCount are
// fixed at prepare time: the server pump writes them and the ConcurrentHashMap publishes them to the
// client thread. The two mutable fields are only ever touched on the client thread (executes, long data
// and resets are all client commands), so they need no further synchronization: paramTypes caches the
// last resent parameter types (a re-execute with new-params-bound-flag = 0 omits them), and
// longDataParams marks the parameters whose value arrived via COM_STMT_SEND_LONG_DATA (absent from the
// execute packet, rendered as an explicit marker).
private class PreparedStatementRecord(val query: String, val paramCount: Int) {
    var paramTypes: IntArray? = null
    val longDataParams = mutableSetOf<Int>()
}

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

    // clientOutput has two writers: the server->client pump (normal server bytes) and abortSession (an
    // injected ERR packet, callable from either relay thread). The lock serializes them so one write cannot
    // be split by the other, and -- because the pump's forward and the abort both consult sessionAborted
    // while holding it -- guarantees no server chunk is forwarded after the abort ERR. sessionAborted is
    // only ever accessed under the lock, which supplies the happens-before edge. A ReentrantLock rather than
    // a monitor so abortSession can take it with a timeout: the pump may be parked in a blocking, unbounded
    // write to a client that stopped reading while holding this lock, and a plain synchronized abort would
    // block forever behind it (pinning both threads and the upstream connection).
    private val clientWriteLock = ReentrantLock()
    private var sessionAborted: Boolean = false

    // MySQL assigns prepared-statement ids server-side, so a COM_STMT_PREPARE's query text can only be paired
    // with its id when the prepare-ok response arrives. The proxy can only recognise that response
    // heuristically (a 12-byte 0x00 packet, or a 0xFF ERR), which is reliable only when exactly one prepare
    // is outstanding and no other command's response can interleave. So at most one prepare is tracked at a
    // time: the client thread stores the in-flight prepare's text, the server pump pairs it with the id in
    // the next prepare-ok (or drops it on an ERR). A client that pipelines a second prepare before the first
    // response arrives is failed closed rather than mis-audited -- pairing the wrong text to an id (or worse,
    // overwriting a live id's text) would falsify the audit log. All three fields are guarded by prepareLock.
    private val prepareLock = Any()
    private var prepareInFlight = false
    private var inFlightPrepareQuery: String? = null
    private val preparedQueries = ConcurrentHashMap<Int, PreparedStatementRecord>()

    private val clientParser = MySqlClientPacketParser(
        onQuery = { query ->
            auditQuery(query)
        },
        onPrepare = { query ->
            synchronized(prepareLock) {
                // Fail closed on a pipelined prepare: with one already awaiting its response, the proxy
                // cannot tell which response belongs to which prepare, so it cannot audit them reliably.
                if (prepareInFlight) {
                    throw FailClosedException(
                        "Kviklet proxy received a COM_STMT_PREPARE while a previous prepare was still " +
                            "awaiting its response; pipelined prepares cannot be reliably audited. The " +
                            "command was blocked and the session closed.",
                    )
                }
                prepareInFlight = true
                inFlightPrepareQuery = query
            }
        },
        onExecute = { stmtId, payload ->
            // Fail closed: an execute the proxy cannot attribute to query text must not reach the server.
            // Ids go untracked when the prepare-response pairing was disturbed (a non-prepare response
            // interleaved with the prepare) or when the client fabricates an id it never prepared here.
            val statement = preparedQueries[stmtId]
                ?: throw FailClosedException(
                    "Kviklet proxy does not know the prepared statement id $stmtId and cannot audit this " +
                        "execution. The query was blocked and the session closed.",
                )
            // Best effort: render the execute's binary parameter values into the placeholder text so the
            // audit log shows what actually ran. A payload the decoder cannot fully and unambiguously
            // decode falls back to the placeholder text -- a wrong value in the log would be worse than no
            // value, and blocking would break legitimate clients over an audit nicety.
            val interpolated = interpolateExecutePayload(
                statement.query,
                statement.paramCount,
                statement.paramTypes,
                statement.longDataParams,
                payload,
            )
            // Long data is consumed by this execute: the protocol scopes COM_STMT_SEND_LONG_DATA to the
            // next execute (a client that streams again re-marks the parameters), so a mark must never
            // leak into a later execute where the parameter's value is back in the packet.
            statement.longDataParams.clear()
            // Track the types the server now holds for this statement even when nothing was interpolated:
            // a later execute with new-params-bound-flag = 0 does not resend them, and decoding it against
            // an out-of-date cache would render plausible but wrong values.
            statement.paramTypes = interpolated.paramTypes
            if (interpolated.query == null) {
                logger.warn(
                    "Could not decode the parameters of prepared statement $stmtId; " +
                        "auditing its placeholder text",
                )
                auditQuery(statement.query)
            } else {
                auditQuery(interpolated.query)
            }
        },
        onLongData = { stmtId, paramIndex ->
            // The parameter's bytes travel ahead of the execute and are not repeated in the execute
            // packet, so the execute decoder must skip that parameter; it renders as an explicit marker.
            // An unknown id needs no action: its execute fails closed anyway.
            preparedQueries[stmtId]?.longDataParams?.add(paramIndex)
        },
        onStmtReset = { stmtId ->
            // COM_STMT_RESET discards any accumulated long data server-side before an execute ever
            // consumes it.
            preparedQueries[stmtId]?.longDataParams?.clear()
        },
        onClose = { stmtId ->
            // Release the stored query when the client closes the prepared statement
            preparedQueries.remove(stmtId)
        },
        onQuit = {
            terminationMessageReceived = true
        },
        onResetConnection = {
            // The server forgets every prepared statement and reassigns ids from scratch, so drop the
            // proxy's tracking too or a reused id would resolve to a stale (wrong) query text.
            synchronized(prepareLock) {
                prepareInFlight = false
                inFlightPrepareQuery = null
                preparedQueries.clear()
            }
        },
    )

    private val serverParser = MySqlServerPacketParser(
        onPrepareOk = { stmtId, paramCount ->
            synchronized(prepareLock) {
                // Only pair while a prepare is actually outstanding; otherwise this is an ordinary OK packet
                // that merely happens to be 12 bytes, not a prepare-ok.
                if (prepareInFlight) {
                    val query = inFlightPrepareQuery
                    prepareInFlight = false
                    inFlightPrepareQuery = null
                    // A fresh prepare-ok must never collide with a live id (the client closes an id before it
                    // can be reassigned). A collision means a non-prepare response was misread as a
                    // prepare-ok: the statement stream is out of sync, so fail closed rather than overwrite a
                    // tracked statement's text and falsify its future audit entries.
                    if (query != null &&
                        preparedQueries.putIfAbsent(stmtId, PreparedStatementRecord(query, paramCount)) != null
                    ) {
                        throw FailClosedException(
                            "Kviklet proxy saw prepared statement id $stmtId assigned while it was still in " +
                                "use; the statement stream is out of sync and cannot be audited. The " +
                                "session was closed.",
                        )
                    }
                }
            }
        },
        onPrepareErr = {
            // An ERR while a prepare is outstanding is taken as that prepare failing: drop its text so a
            // later prepare-ok is not paired with it. (If the ERR actually answered an interleaved command,
            // the real prepare-ok then finds no prepare in flight and its id goes untracked -- its execute
            // fails closed, which is safe.)
            synchronized(prepareLock) {
                if (prepareInFlight) {
                    prepareInFlight = false
                    inFlightPrepareQuery = null
                }
            }
        },
    )

    private fun auditQuery(query: String) {
        try {
            val executePayload = ExecutePayload(query = query)
            eventService.saveEvent(executionRequest.id!!, userId, executePayload)
        } catch (e: Exception) {
            // Fail closed: a query the audit log did not record must not reach the server.
            throw FailClosedException(
                "Kviklet could not record the query in the audit log. The query was blocked and the " +
                    "session closed.",
                e,
            )
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

    // Tells the client why the session is being killed (an ERR packet), then closes both sockets. The ERR
    // is written under clientWriteLock so a concurrent server->client chunk cannot split it and nothing is
    // forwarded to the client after it; close() then ends both relay loops.
    //
    // The ERR is best effort: the lock is taken with a timeout, because the pump may be holding it in a
    // blocking write to a client that has stopped reading. Waiting for it unconditionally would deadlock the
    // abort -- and therefore teardown -- behind that write. On a timeout the ERR is skipped and close() runs
    // anyway; closing the sockets unblocks that parked write, so both relay threads still exit.
    private fun abortSession(reason: String) {
        val locked = try {
            clientWriteLock.tryLock(ABORT_ERR_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
        if (locked) {
            try {
                // Sequence id 1: the abort is triggered by a client command packet (sequence id 0), and 1
                // is where the client expects that command's response. An abort raised from the server side
                // mid-response may mismatch the client's expected sequence, which is acceptable for a
                // session that is being torn down.
                writePacket(clientOutput, 1, buildErrPacket(1105, "HY000", reason))
                sessionAborted = true
            } catch (e: Exception) {
                logger.warn("Failed to send the ERR packet to the client while aborting the session", e)
            } finally {
                clientWriteLock.unlock()
            }
        } else {
            logger.warn(
                "Could not acquire the client write lock to send an abort ERR (relay write in progress); closing",
            )
        }
        close()
    }

    override fun startHandling() {
        // Two blocking threads per session. This (pool) thread drives client->server: it feeds the packet
        // parser (which audits queries and tracks prepared statements) and forwards the raw bytes. A single
        // spawned thread pumps server->client, feeding its parser only to match prepared-statement ids.
        // The finally is the single teardown path for every exit: clean COM_QUIT, client/server EOF, an
        // aborted session or an exception. It closes both sockets (freeing the upstream connection) which
        // also unblocks the pump thread, then joins it.
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
                // Fail closed: nothing is forwarded to the server unless the whole chunk was parsed and
                // audited. The parser callbacks run synchronously inside addBytes, so every query is
                // recorded before its bytes reach the server; a violation drops the entire chunk (an
                // already-audited packet in it is dropped too, which is safe -- audited-but-never-executed
                // is fine, forwarded-but-never-audited is not) and aborts the session with an ERR packet.
                try {
                    clientParser.addBytes(chunk)
                } catch (e: FailClosedException) {
                    logger.warn("Blocking client traffic and closing the session: ${e.message}", e.cause ?: e)
                    abortSession(e.message!!)
                    break
                } catch (e: Exception) {
                    logger.error("Failed to parse client traffic, blocking it and closing the session", e)
                    abortSession(
                        "Kviklet proxy could not parse the client message. The message was blocked and " +
                            "the session closed.",
                    )
                    break
                }
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
                try {
                    serverParser.addBytes(chunk)
                } catch (e: FailClosedException) {
                    // The server side fails closed only when a prepare-ok is assigned an id that is still in
                    // use (a sign the statement stream is out of sync); large result payloads are streamed
                    // past, not parsed, so they never reach here.
                    logger.warn("Blocking server traffic and closing the session: ${e.message}", e.cause ?: e)
                    abortSession(e.message!!)
                    break
                }
                clientWriteLock.lock()
                try {
                    // If an abort sent its ERR packet while this chunk was being read, stop rather than
                    // forwarding server bytes after the error the client already saw.
                    if (sessionAborted) break
                    clientOutput.writeAndFlush(chunk)
                } finally {
                    clientWriteLock.unlock()
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
}

// Any control packet the parsers actually inspect (a 12-byte prepare-ok, a small ERR) is far under this, so
// in the streaming (server) direction a packet larger than this needs neither buffering nor inspection: it
// is streamed past. Generous enough to never clip a real ERR message.
private const val MAX_STREAMED_CONTROL_PACKET = 4096

// Slices a raw MySQL byte stream into logical packets, invoking [onPacket] once per full payload (the 4-byte
// length + sequence header is consumed here). It consumes the input incrementally -- a byte is copied at most
// once and a partial packet is never re-copied between calls -- so there is no O(n^2) buffer churn under
// chunked reads. A payload of exactly 0xFFFFFF means the logical payload continues in the following
// packet(s).
//
// [reassemble] chooses what happens to those split payloads, which is the only difference between the two
// directions:
//   - client->server (reassemble = true): split payloads are joined and emitted whole (bounded by
//     MAX_ASSEMBLED_PAYLOAD_LENGTH), so a >16MB statement is audited verbatim instead of being misread as a
//     truncated query plus garbage commands.
//   - server->client (reassemble = false): the parser only inspects short control packets, so any payload
//     over MAX_STREAMED_CONTROL_PACKET (including every 16MB+ split result row) is streamed past without
//     buffering and never emitted. This keeps server-direction memory at O(1): a large BLOB result can no
//     longer pin up to 1GB of heap and trip an OutOfMemoryError that would bypass the fail-closed handlers.
private class MySqlPacketFramer(private val reassemble: Boolean, private val onPacket: (ByteArray) -> Unit) {
    private val header = ByteArray(4)
    private var headerFilled = 0
    private var payloadRemaining = 0
    private var currentIsContinuation = false

    // True while we are in the middle of a split logical payload (the previous packet was a 0xFFFFFF piece).
    private var continuingSplit = false

    // True while streaming past a payload we will not emit (server direction only). Decided once, at the
    // first packet of a logical payload, and held for the whole split chain.
    private var skipping = false

    // Accumulates the payload to emit: one small packet, or a reassembled split payload in reassemble mode.
    private val payload = ByteArrayOutputStream()

    @Synchronized
    fun addBytes(bytes: ByteArray) {
        var i = 0
        while (i < bytes.size) {
            if (headerFilled < 4) {
                val take = minOf(4 - headerFilled, bytes.size - i)
                System.arraycopy(bytes, i, header, headerFilled, take)
                headerFilled += take
                i += take
                if (headerFilled < 4) break // wait for the rest of the header
                val length = (header[0].toInt() and 0xFF) or
                    ((header[1].toInt() and 0xFF) shl 8) or
                    ((header[2].toInt() and 0xFF) shl 16)
                currentIsContinuation = length == MAX_SPLIT_PACKET_LENGTH
                payloadRemaining = length
                if (!continuingSplit) {
                    // First packet of a new logical payload: decide once whether to buffer or stream past it.
                    skipping = !reassemble && (currentIsContinuation || length > MAX_STREAMED_CONTROL_PACKET)
                }
                if (reassemble && payload.size() + length > MAX_ASSEMBLED_PAYLOAD_LENGTH) {
                    throw FailClosedException(
                        "Kviklet proxy does not support MySQL payloads larger than 1GB. The packet was " +
                            "blocked and the session closed.",
                    )
                }
            }
            if (payloadRemaining > 0) {
                val take = minOf(payloadRemaining, bytes.size - i)
                if (!skipping) payload.write(bytes, i, take)
                payloadRemaining -= take
                i += take
            }
            if (headerFilled == 4 && payloadRemaining == 0) {
                headerFilled = 0
                if (currentIsContinuation) {
                    continuingSplit = true // more packets belong to this logical payload
                } else {
                    continuingSplit = false
                    if (!skipping) onPacket(payload.toByteArray())
                    payload.reset()
                    skipping = false
                }
            }
        }
    }
}

class MySqlClientPacketParser(
    private val onQuery: (String) -> Unit,
    private val onPrepare: (String) -> Unit,
    // Receives the statement id and the full COM_STMT_EXECUTE payload, so the listener can decode the
    // bound parameter values for the audit log.
    private val onExecute: (Int, ByteArray) -> Unit,
    private val onClose: (Int) -> Unit = {},
    private val onQuit: () -> Unit,
    private val onResetConnection: () -> Unit = {},
    // Statement id and parameter index of a COM_STMT_SEND_LONG_DATA (the data bytes are not passed on).
    private val onLongData: (Int, Int) -> Unit = { _, _ -> },
    private val onStmtReset: (Int) -> Unit = {},
) {
    // reassemble = true: a >16MB client statement is joined so its full text can be audited verbatim.
    private val framer = MySqlPacketFramer(reassemble = true) { payload -> handlePacket(payload) }

    fun addBytes(bytes: ByteArray) = framer.addBytes(bytes)

    // Dispatches one client command packet. This is a default-deny allowlist: a command is handled here only
    // if the proxy can either audit its SQL or be sure it carries none, and everything else fails closed.
    // Forwarding an unrecognised command would relay traffic the audit log never saw (COM_BINLOG_DUMP streams
    // every row change, COM_CHANGE_USER re-authenticates, legacy COM_CREATE_DB/COM_DROP_DB run schema DDL).
    // There is deliberately no catch-all around the dispatch: a violation (or an unexpected parsing error)
    // must propagate so the relay blocks the packet and aborts the session.
    private fun handlePacket(payload: ByteArray) {
        if (payload.isEmpty()) return
        val cmd = payload[0].toInt() and 0xFF
        when (cmd) {
            0x01 -> onQuit()

            // COM_QUIT

            0x02 -> { // COM_INIT_DB
                // Switching the default schema changes what the unqualified names in later audited queries
                // resolve to, so record it as an explicit USE instead of letting it pass unaudited.
                val db = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                onQuery("USE `" + db.replace("`", "``") + "`")
            }

            0x03 -> { // COM_QUERY
                val query = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                if (query.trim().isNotEmpty()) {
                    onQuery(query)
                }
            }

            0x16 -> { // COM_STMT_PREPARE
                // Every prepare is tracked, even a blank one: the server answers each with exactly one
                // response, and the in-flight pairing relies on nothing being skipped.
                val query = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                onPrepare(query)
            }

            0x17 -> onExecute(readStatementId(payload, "COM_STMT_EXECUTE"), payload)

            // COM_STMT_EXECUTE

            // COM_STMT_SEND_LONG_DATA: statement id (4 bytes) then parameter index (2 bytes, little-endian)
            // after the command byte. The data bytes are relayed but not passed on -- the execute they belong
            // to is audited with an explicit long-data marker for this parameter.
            0x18 -> {
                if (payload.size < 7) {
                    throw FailClosedException(
                        "Kviklet proxy could not parse a truncated COM_STMT_SEND_LONG_DATA packet. The " +
                            "packet was blocked and the session closed.",
                    )
                }
                val paramIndex = (payload[5].toInt() and 0xFF) or ((payload[6].toInt() and 0xFF) shl 8)
                onLongData(readStatementId(payload, "COM_STMT_SEND_LONG_DATA"), paramIndex)
            }

            0x19 -> onClose(readStatementId(payload, "COM_STMT_CLOSE"))

            // COM_STMT_CLOSE

            // COM_STMT_RESET discards a statement's accumulated long data server-side, so the audit-side
            // long-data tracking must be dropped with it.
            0x1A -> onStmtReset(readStatementId(payload, "COM_STMT_RESET"))

            // COM_RESET_CONNECTION forgets every server-side prepared statement (and reassigns ids from
            // scratch), so the proxy must drop its own tracking to stay in sync.
            0x1F -> onResetConnection()

            // Commands that carry no auditable SQL and cannot defeat the audit, relayed unchanged:
            //   COM_STATISTICS, COM_PING            -- server-info / liveness, no SQL
            //   COM_STMT_FETCH                      -- cursor-read of an already-audited statement
            //   COM_SET_OPTION                      -- multi-statements stay auditable (COM_QUERY text is
            //                                          captured verbatim, all statements included)
            0x09, 0x0E, 0x1B, 0x1C -> {}

            else -> throw FailClosedException(blockedCommandMessage(cmd))
        }
    }

    // The 4-byte little-endian statement id following the command byte. A packet too short to carry it is
    // garbage the server cannot meaningfully execute either; fail closed rather than forwarding it.
    private fun readStatementId(payload: ByteArray, commandName: String): Int {
        if (payload.size < 5) {
            throw FailClosedException(
                "Kviklet proxy could not parse a truncated $commandName packet. The packet was blocked " +
                    "and the session closed.",
            )
        }
        return (payload[1].toInt() and 0xFF) or
            ((payload[2].toInt() and 0xFF) shl 8) or
            ((payload[3].toInt() and 0xFF) shl 16) or
            ((payload[4].toInt() and 0xFF) shl 24)
    }

    private fun blockedCommandMessage(cmd: Int): String {
        val name = KNOWN_COMMAND_NAMES[cmd] ?: "0x%02x".format(cmd)
        return "Kviklet proxy does not permit MySQL command $name because it cannot be audited or bypasses " +
            "the audited session. The command was blocked and the session closed."
    }

    companion object {
        // Names for the more notable blocked commands, only to make the abort message and logs readable;
        // any command not on the allowlist is blocked whether or not it appears here.
        private val KNOWN_COMMAND_NAMES = mapOf(
            0x04 to "COM_FIELD_LIST",
            0x05 to "COM_CREATE_DB",
            0x06 to "COM_DROP_DB",
            0x07 to "COM_REFRESH",
            0x08 to "COM_SHUTDOWN",
            0x0C to "COM_PROCESS_KILL",
            0x11 to "COM_CHANGE_USER",
            0x12 to "COM_BINLOG_DUMP",
            0x13 to "COM_TABLE_DUMP",
            0x1E to "COM_BINLOG_DUMP_GTID",
        )
    }
}

// onPrepareOk receives the assigned statement id and the statement's parameter count, both read from the
// prepare-ok payload; the count is what lets the execute decoder parse the binary parameter section.
class MySqlServerPacketParser(private val onPrepareOk: (Int, Int) -> Unit, private val onPrepareErr: () -> Unit = {}) {
    // reassemble = false: the parser only inspects short control packets, so large result payloads (a split
    // multi-megabyte BLOB row) are streamed past without buffering rather than accumulated up to 1GB.
    private val framer = MySqlPacketFramer(reassemble = false) { payload -> handlePacket(payload) }

    fun addBytes(bytes: ByteArray) = framer.addBytes(bytes)

    private fun handlePacket(payload: ByteArray) {
        if (payload.isEmpty()) return
        val status = payload[0].toInt() and 0xFF
        // COM_STMT_PREPARE_OK has a fixed 12-byte payload (0x00 status + 4 stmt_id + 2 columns + 2 params
        // + 1 reserved + 2 warnings). Requiring the exact length avoids mistaking a generic OK packet
        // (also 0x00) for a prepare-ok. ERR packets (0xFF) pop the pending prepare so a later OK is not
        // misread. Both callbacks no-op when no prepare is outstanding, which keeps ordinary resultset
        // and error traffic from being misread; a client interleaving other commands with an outstanding
        // prepare can still disturb the pairing, and the executes of a mispaired id then fail closed.
        if (status == 0xFF) {
            onPrepareErr()
        } else if (status == 0x00 && payload.size == 12) {
            val stmtId = (payload[1].toInt() and 0xFF) or
                ((payload[2].toInt() and 0xFF) shl 8) or
                ((payload[3].toInt() and 0xFF) shl 16) or
                ((payload[4].toInt() and 0xFF) shl 24)
            val paramCount = (payload[7].toInt() and 0xFF) or ((payload[8].toInt() and 0xFF) shl 8)
            onPrepareOk(stmtId, paramCount)
        }
    }
}
