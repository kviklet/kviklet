// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.postgres.messages.authenticationOk
import dev.kviklet.kviklet.proxy.postgres.messages.backendKeyData
import dev.kviklet.kviklet.proxy.postgres.messages.createAuthenticationSASLStartMessage
import dev.kviklet.kviklet.proxy.postgres.messages.gssEncNotSupportedMessage
import dev.kviklet.kviklet.proxy.postgres.messages.isCancelRequest
import dev.kviklet.kviklet.proxy.postgres.messages.isGSSENCRequest
import dev.kviklet.kviklet.proxy.postgres.messages.isSSLRequest
import dev.kviklet.kviklet.proxy.postgres.messages.isStartupMessage
import dev.kviklet.kviklet.proxy.postgres.messages.paramMessage
import dev.kviklet.kviklet.proxy.postgres.messages.readyForQuery
import dev.kviklet.kviklet.proxy.postgres.messages.startupMessageUser
import dev.kviklet.kviklet.proxy.postgres.messages.tlsNotSupportedMessage
import dev.kviklet.kviklet.proxy.postgres.messages.tlsSupportedMessage
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import javax.net.ssl.SSLSocket

// Postgres' own cap on a startup packet (PQ_MAX_STARTUP_PACKET_LENGTH). A declared length beyond what
// the real server would accept can only be garbage or an attack, so we reject it instead of buffering.
private const val MAX_STARTUP_PACKET_LENGTH = 10_000

// A legitimate client sends at most one SSLRequest and one GSSENCRequest before its StartupMessage. More
// pre-startup frames than this means the client is looping (e.g. repeated SSLRequests, each of which would
// also nest another TLS layer) and it is cut off rather than allowed to spin within the handshake deadline.
private const val MAX_PRE_STARTUP_FRAMES = 4

// Fallback total handshake budget used only if the caller left the socket timeout at 0 ("block forever").
private const val DEFAULT_HANDSHAKE_BUDGET_MS = 10_000L

// The (possibly TLS-upgraded) client socket once the client has successfully authenticated, together with
// the session its username routed to. The session is what a single stable listener uses to serve many
// concurrent requests: the client delivers its username in the startup message, before any upstream work.
class AuthenticatedSession(val socket: Socket, val session: ProxySession)

// Runs SSL negotiation and client authentication, routing to a session by the username in the startup
// message. No upstream database connection is opened here, so an unauthenticated, aborting or idle client
// can never leak a target connection or pin a slot: the caller's handshake deadline (soTimeout) bounds
// every read below, and EOF ends the handshake.
//
// resolveSession returns the session for a username, or null if there is none. An unknown username is not
// short-circuited: the full SASL exchange still runs (with unknownUserPassword) so it fails identically to a
// wrong password and an attacker cannot enumerate valid usernames. waitUntilAuthenticated throws on any auth
// failure, so returning normally guarantees a matched, authenticated session.
//
// Returns null when the client goes away before authenticating (immediate close, or a CancelRequest that
// never authenticates and has nothing to relay).
fun authenticateClient(
    client: Socket,
    tlsCert: TLSCertificate?,
    unknownUserPassword: String,
    resolveSession: (String) -> ProxySession?,
): AuthenticatedSession? {
    // The caller's soTimeout is the TOTAL handshake budget, not just a per-read timeout: the socket timeout
    // is shrunk to the remaining budget before every read below, so the whole handshake -- including a slow
    // client dribbling one valid frame just under each timeout -- cannot outlast it and hold its slot forever.
    val budgetMs = if (client.soTimeout > 0) client.soTimeout.toLong() else DEFAULT_HANDSHAKE_BUDGET_MS
    val deadline = System.currentTimeMillis() + budgetMs
    var socket = client
    var input = socket.getInputStream()
    var output = socket.getOutputStream()
    var preStartupFrames = 0

    while (true) {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) {
            throw Exception("Client handshake exceeded its deadline, aborting the connection")
        }
        socket.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

        // Read the whole frame before dispatching. A single read is not guaranteed to be a single
        // frame: TCP can split one, and the fixed-offset detectors below would misread a half-frame
        // (throwing on a split header, or auth-failing a valid user and feeding the tail to SASL on a
        // split StartupMessage). The relay loop was framed for the same reason in KVI-231.
        val frame = readStartupFrame(input) ?: return null // client closed before a complete frame

        when {
            isSSLRequest(frame) -> {
                socket = handleSSLRequest(socket, tlsCert)
                input = socket.getInputStream()
                output = socket.getOutputStream()
                preStartupFrames++
            }

            isGSSENCRequest(frame) -> {
                // We do not offer GSSAPI encryption, decline it and let the client fall back to a
                // plain StartupMessage instead of discarding the request and deadlocking.
                output.writeAndFlush(gssEncNotSupportedMessage())
                preStartupFrames++
            }

            isCancelRequest(frame) -> {
                // A CancelRequest opens a throwaway connection carrying no startup message and never
                // authenticates. The proxy hands out zeroed backend key data, so there is nothing to
                // cancel: drop it rather than block forever waiting for a startup message.
                return null
            }

            isStartupMessage(frame) -> {
                val requestedUser = startupMessageUser(frame, frame.size)
                val session = requestedUser?.let { resolveSession(it) }
                sendAuthRequest(output)
                // Unknown user -> isUserValid=false and a placeholder password, so SASL fails exactly like a
                // wrong password. waitUntilAuthenticated throws on failure, so the return below is only
                // reached when a real session authenticated successfully.
                waitUntilAuthenticated(input, output, session?.password ?: unknownUserPassword, session != null)
                return AuthenticatedSession(socket, session!!)
            }

            else -> throw Exception("Unexpected message during the client handshake, aborting the connection")
        }

        if (preStartupFrames > MAX_PRE_STARTUP_FRAMES) {
            throw Exception("Too many pre-startup frames before a StartupMessage, aborting the connection")
        }
    }
}

// Reads exactly one startup-phase frame (SSLRequest, GSSENCRequest, CancelRequest or StartupMessage),
// accumulating across TCP-split reads until the length declared in the first four bytes is satisfied.
// Each of these frames is followed by a server response the client waits for, so only one frame is ever
// in flight. Returns null on EOF before a complete frame; the socket timeout still bounds each read.
fun readStartupFrame(input: InputStream): ByteArray? {
    val header = ByteArray(4)
    if (!readFully(input, header, 0, 4)) {
        return null
    }
    // The length is a big-endian int32 that includes the four length bytes themselves; the smallest
    // valid frame is 8 bytes (SSLRequest / GSSENCRequest).
    val declaredLength = ByteBuffer.wrap(header).int
    if (declaredLength < 8 || declaredLength > MAX_STARTUP_PACKET_LENGTH) {
        throw Exception("Invalid startup frame length $declaredLength, aborting the connection")
    }
    val frame = ByteArray(declaredLength)
    header.copyInto(frame)
    if (!readFully(input, frame, 4, declaredLength)) {
        return null
    }
    return frame
}

// Fills buffer[from until to], looping over partial reads. Returns false on EOF before `to` is reached.
private fun readFully(input: InputStream, buffer: ByteArray, from: Int, to: Int): Boolean {
    var offset = from
    while (offset < to) {
        val read = input.read(buffer, offset, to - offset)
        if (read == -1) {
            return false
        }
        offset += read
    }
    return true
}

// Sent after the client has authenticated and the upstream connection has been opened: tells the
// client authentication succeeded and forwards the upstream server's runtime parameters so the client
// sees the real backend configuration.
fun finishClientStartup(socket: Socket, params: Map<String, String>) {
    val output = socket.getOutputStream()
    output.writeAndFlush(authenticationOk())
    sendParameters(output, params)
    output.writeAndFlush(backendKeyData())
    output.writeAndFlush(readyForQuery())
}

fun handleSSLRequest(client: Socket, cert: TLSCertificate?): Socket {
    val response = if (cert == null) tlsNotSupportedMessage() else tlsSupportedMessage()
    client.getOutputStream().writeAndFlush(response)
    return if (cert == null) client else enableSSL(client, cert)
}

fun enableSSL(clientSocket: Socket, cert: TLSCertificate): Socket {
    val sslSocket = cert.sslContext.socketFactory.createSocket(
        clientSocket,
        null,
        clientSocket.getPort(),
        false,
    ) as SSLSocket
    sslSocket.useClientMode = false
    return sslSocket
}

fun sendAuthRequest(output: OutputStream) {
    val authRequest = createAuthenticationSASLStartMessage()
    output.writeAndFlush(authRequest)
}

/* If the user is invalid the error is not delivered straight away. Instead it is passed to the SASL
 * flow, which cancels authentication once a password is sent, so an attacker cannot tell a wrong
 * username from a wrong password and enumerate valid users.
 */
fun waitUntilAuthenticated(input: InputStream, output: OutputStream, password: String, isUserValid: Boolean) {
    val handler = SASLAuthHandler(output, input, password, isUserValid)
    handler.handle()
}

fun sendParameters(output: OutputStream, params: Map<String, String>) {
    for (param in params) {
        output.write(paramMessage(param.key, param.value))
    }
    output.flush()
}
