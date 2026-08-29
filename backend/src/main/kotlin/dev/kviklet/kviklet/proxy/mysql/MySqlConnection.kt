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
    private val preparedQueries = ConcurrentHashMap<Int, String>()

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
        onExecute = { stmtId ->
            // Fail closed: an execute the proxy cannot attribute to query text must not reach the server.
            // Ids go untracked when the prepare-response pairing was disturbed (a non-prepare response
            // interleaved with the prepare) or when the client fabricates an id it never prepared here.
            val query = preparedQueries[stmtId]
                ?: throw FailClosedException(
                    "Kviklet proxy does not know the prepared statement id $stmtId and cannot audit this " +
                        "execution. The query was blocked and the session closed.",
                )
            auditQuery(query)
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
                    if (query != null && preparedQueries.putIfAbsent(stmtId, query) != null) {
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
                    // The server side only fails closed on unbounded split packets; without parsing them the
                    // prepared-statement tracking (and so the audit) can no longer be trusted.
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

// Buffers a raw MySQL byte stream and slices it into complete logical packets, invoking [onPacket] once per
// full payload (the 4-byte length + sequence header is consumed here). A partial packet stays buffered until
// the rest of it arrives, so only the incomplete tail is ever re-copied between calls. A payload of exactly
// 0xFFFFFF means the logical payload continues in the following packet(s); those are reassembled into one
// payload before [onPacket] runs, so a >16MB statement is audited whole instead of being misread as a
// truncated query plus garbage commands. Shared by the client and server parsers, which differ only in what
// they do with a payload.
private class MySqlPacketFramer(private val onPacket: (ByteArray) -> Unit) {
    private val buffer = ByteArrayOutputStream()
    private val pendingSplitPayload = ByteArrayOutputStream()

    @Synchronized
    fun addBytes(bytes: ByteArray) {
        buffer.write(bytes)
        val data = buffer.toByteArray()
        var offset = 0
        while (data.size - offset >= 4) {
            val length = (data[offset].toInt() and 0xFF) or
                ((data[offset + 1].toInt() and 0xFF) shl 8) or
                ((data[offset + 2].toInt() and 0xFF) shl 16)

            if (data.size - offset < 4 + length) {
                break // Need more data for a full packet
            }

            if (pendingSplitPayload.size() + length > MAX_ASSEMBLED_PAYLOAD_LENGTH) {
                throw FailClosedException(
                    "Kviklet proxy does not support MySQL payloads larger than 1GB. The packet was " +
                        "blocked and the session closed.",
                )
            }

            if (length == MAX_SPLIT_PACKET_LENGTH) {
                // The logical payload continues in the next packet; buffer this piece and keep going.
                pendingSplitPayload.write(data, offset + 4, length)
            } else if (pendingSplitPayload.size() > 0) {
                // The final piece of a split payload: reassemble and emit as one logical packet.
                pendingSplitPayload.write(data, offset + 4, length)
                val payload = pendingSplitPayload.toByteArray()
                pendingSplitPayload.reset()
                onPacket(payload)
            } else {
                val payload = ByteArray(length)
                System.arraycopy(data, offset + 4, payload, 0, length)
                onPacket(payload)
            }

            offset += 4 + length
        }

        buffer.reset()
        if (data.size > offset) {
            buffer.write(data, offset, data.size - offset)
        }
    }
}

class MySqlClientPacketParser(
    private val onQuery: (String) -> Unit,
    private val onPrepare: (String) -> Unit,
    private val onExecute: (Int) -> Unit,
    private val onClose: (Int) -> Unit = {},
    private val onQuit: () -> Unit,
) {
    private val framer = MySqlPacketFramer { payload -> handlePacket(payload) }

    fun addBytes(bytes: ByteArray) = framer.addBytes(bytes)

    // Dispatches one client command packet to its callback. Deliberately without a catch-all: a violation
    // (or an unexpected parsing error) must propagate so the relay blocks the packet and aborts the
    // session -- swallowing it here would forward traffic the audit log never saw.
    private fun handlePacket(payload: ByteArray) {
        if (payload.isEmpty()) return
        val cmd = payload[0].toInt() and 0xFF
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

            // Legacy schema DDL and mid-session re-authentication carry no auditable SQL text, so they
            // must not pass through an audited session. No modern client sends them.
            0x05 -> throw FailClosedException(blockedCommandMessage("COM_CREATE_DB"))

            0x06 -> throw FailClosedException(blockedCommandMessage("COM_DROP_DB"))

            0x11 -> throw FailClosedException(blockedCommandMessage("COM_CHANGE_USER"))

            0x16 -> { // COM_STMT_PREPARE
                // Every prepare is tracked, even a blank one: the server answers each with exactly one
                // response, and the response pairing (see pendingPrepares) relies on nothing being skipped.
                val query = String(payload, 1, payload.size - 1, Charsets.UTF_8)
                onPrepare(query)
            }

            0x17 -> { // COM_STMT_EXECUTE
                onExecute(readStatementId(payload, "COM_STMT_EXECUTE"))
            }

            0x19 -> { // COM_STMT_CLOSE
                onClose(readStatementId(payload, "COM_STMT_CLOSE"))
            }
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

    private fun blockedCommandMessage(commandName: String): String =
        "Kviklet proxy does not support $commandName because it bypasses the audited session. The " +
            "command was blocked and the session closed."
}

class MySqlServerPacketParser(private val onPrepareOk: (Int) -> Unit, private val onPrepareErr: () -> Unit = {}) {
    private val framer = MySqlPacketFramer { payload -> handlePacket(payload) }

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
            onPrepareOk(stmtId)
        }
    }
}
