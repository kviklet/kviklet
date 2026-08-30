// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.proxy.core.AuthenticatedClient
import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.core.TLSCertificate
import dev.kviklet.kviklet.proxy.core.enableSSL
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

// The only auth plugin the proxy speaks on its client side. The client's password is a Kviklet-generated
// temp credential, so the weaker (deprecated upstream) native scramble is acceptable here; the upstream
// side negotiates whatever the real server offers (see TargetMySqlServer.kt).
const val NATIVE_PASSWORD_PLUGIN = "mysql_native_password"

private const val CLIENT_SSL = 0x0800
private const val CLIENT_PROTOCOL_41 = 0x0200
private const val CLIENT_COMPRESS = 0x0020
private const val CLIENT_LOCAL_FILES = 0x0080
private const val CLIENT_ZSTD_COMPRESSION = 0x04000000
private const val COM_QUIT = 0x01

// Generous cap on any client packet during the handshake. A HandshakeResponse is far smaller; a declared
// length beyond this can only be garbage or an attack, so it is rejected instead of buffered (the header
// alone could otherwise make the proxy allocate 16MB per probe).
private const val MAX_HANDSHAKE_PACKET_LENGTH = 10_000

// Fallback total handshake budget used only if the caller left the socket timeout at 0 ("block forever").
private const val DEFAULT_HANDSHAKE_BUDGET_MS = 10_000L

// Runs the server-speaks-first MySQL handshake and client authentication, routing to a session by the
// username in the client's HandshakeResponse. No upstream database connection is opened here, so an
// unauthenticated, aborting or idle client can never leak a target connection or pin a slot: the caller's
// handshake deadline (soTimeout) bounds every read below as a total budget, and EOF ends the handshake.
//
// resolveSession returns the session for a username, or null if there is none. An unknown username is not
// short-circuited: the exact same scramble verification still runs (with unknownUserPassword) and fails
// with the identical generic access-denied error a wrong password produces, so an attacker cannot
// enumerate valid usernames.
//
// Returns null when the client goes away before authenticating (immediate close, or a COM_QUIT sent
// instead of a HandshakeResponse -- a liveness probe with nothing to relay).
fun authenticateClientMySql(
    client: Socket,
    tlsCert: TLSCertificate?,
    unknownUserPassword: String,
    resolveSession: (String) -> ProxySession?,
): AuthenticatedClient? {
    // The caller's soTimeout is the TOTAL handshake budget, not just a per-read timeout: the socket
    // timeout is shrunk to the remaining budget before every packet read below, so a slow client dribbling
    // one valid packet just under each timeout cannot outlast it and hold its slot forever.
    val budgetMs = if (client.soTimeout > 0) client.soTimeout.toLong() else DEFAULT_HANDSHAKE_BUDGET_MS
    val deadline = System.currentTimeMillis() + budgetMs
    var socket = client
    var input = socket.getInputStream()
    var output = socket.getOutputStream()

    // MySQL is server-speaks-first: send the initial handshake carrying a fresh scramble, advertising
    // CLIENT_SSL only when a TLS certificate is configured.
    val salt = generateRandomSalt()
    writePacket(output, 0, buildInitialHandshake(1, salt, tlsCert != null))

    var (seq, payload) = readHandshakePacket(socket, input, deadline) ?: return null

    // An SSLRequest is a short HandshakeResponse prefix (capabilities but no username) with CLIENT_SSL
    // set: upgrade to TLS via the shared core helper and read the real HandshakeResponse from the
    // encrypted stream. The client can only legitimately ask when the initial handshake advertised SSL.
    if (payload.size >= 4 && (readClientCapabilities(payload) and CLIENT_SSL) != 0) {
        if (tlsCert == null) {
            writePacket(
                output,
                seq + 1,
                buildErrPacket(2000, "HY000", "SSL connection requested but SSL is not supported by proxy"),
            )
            throw IOException("Client requested SSL but SSL is not supported by proxy")
        }
        socket = enableSSL(client, tlsCert)
        input = socket.getInputStream()
        output = socket.getOutputStream()
        val next = readHandshakePacket(socket, input, deadline) ?: return null
        seq = next.first
        payload = next.second
    }

    // A COM_QUIT instead of a HandshakeResponse is a throwaway connection (liveness probe) that never
    // authenticates and has nothing to relay: drop it rather than failing it as malformed.
    if (payload.size == 1 && (payload[0].toInt() and 0xFF) == COM_QUIT) {
        return null
    }

    // The relay parses every packet on this connection for the audit log, which becomes impossible if the
    // client switches the stream to a form the parser does not understand (compressed packets) or opens a
    // side channel the audit never sees (LOAD DATA LOCAL file transfers). None of these capabilities are
    // advertised in the initial handshake, so a well-behaved client never requests them; one that does
    // anyway is refused up front (fail closed) instead of trusted to not use them.
    if (payload.size < 4) {
        throw IOException("Malformed HandshakeResponse packet (truncated)")
    }
    val clientCapabilities = readClientCapabilities(payload)
    if ((clientCapabilities and CLIENT_PROTOCOL_41) == 0) {
        writePacket(output, seq + 1, buildErrPacket(1105, "HY000", "Kviklet proxy requires a protocol 4.1 client"))
        throw IOException("Client does not speak protocol 4.1")
    }
    val unsupportedCapabilities = listOfNotNull(
        "COMPRESS".takeIf { (clientCapabilities and CLIENT_COMPRESS) != 0 },
        "ZSTD_COMPRESSION".takeIf { (clientCapabilities and CLIENT_ZSTD_COMPRESSION) != 0 },
        "LOCAL_FILES".takeIf { (clientCapabilities and CLIENT_LOCAL_FILES) != 0 },
    )
    if (unsupportedCapabilities.isNotEmpty()) {
        val names = unsupportedCapabilities.joinToString(", ")
        writePacket(
            output,
            seq + 1,
            buildErrPacket(1105, "HY000", "Kviklet proxy does not support the client capabilities: $names"),
        )
        throw IOException("Client requested unsupported capabilities: $names")
    }

    val response = HandshakeResponse.parse(payload)

    // If the client answered for a different auth plugin than the advertised native one, ask it to switch
    // and use the switched response. The switch depends only on the client's plugin choice, never on
    // whether the username resolved, so it leaks nothing.
    var authResponse = response.authResponse
    if (response.authPluginName != null && response.authPluginName != NATIVE_PASSWORD_PLUGIN) {
        seq += 1
        writePacket(output, seq, buildAuthSwitchRequest(salt))
        val switched = readHandshakePacket(socket, input, deadline) ?: return null
        seq = switched.first
        authResponse = switched.second
    }

    // Unknown username -> the same verification runs against a placeholder password, and the failure below
    // is byte-identical to a wrong password, so a probe cannot tell the two apart.
    val session = resolveSession(response.username)
    val isPasswordValid = verifyPassword(salt, session?.password ?: unknownUserPassword, authResponse)
    if (session == null || !isPasswordValid) {
        // Echo the client-supplied username back in the wire-protocol error packet only; never log it or
        // embed it in the server-side exception, to avoid leaking attempted usernames.
        writePacket(
            output,
            seq + 1,
            buildErrPacket(1045, "28000", "Access denied for user '${response.username}' (using password: YES)"),
        )
        throw IOException("Authentication failed: access denied")
    }

    writePacket(output, seq + 1, buildOkPacket())
    // socket is the (possibly TLS-wrapped) stream the client now speaks on; client is the raw accepted TCP
    // socket the relay must close to unblock its threads on teardown.
    return AuthenticatedClient(socket, client, session)
}

// Reads one client packet within the total handshake budget: shrinks the socket timeout to the remaining
// budget first, so the whole handshake cannot outlast the deadline. Returns null on EOF before a complete
// packet (the client went away); throws when the deadline has already passed.
private fun readHandshakePacket(socket: Socket, input: InputStream, deadline: Long): Pair<Int, ByteArray>? {
    val remaining = deadline - System.currentTimeMillis()
    if (remaining <= 0) {
        throw IOException("Client handshake exceeded its deadline, aborting the connection")
    }
    socket.soTimeout = remaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return try {
        readPacket(input, MAX_HANDSHAKE_PACKET_LENGTH)
    } catch (e: EOFException) {
        null
    }
}

// The capability flags a client sends first in both an SSLRequest and a HandshakeResponse
// (little-endian int32 at offset 0).
private fun readClientCapabilities(payload: ByteArray): Int = (payload[0].toInt() and 0xFF) or
    ((payload[1].toInt() and 0xFF) shl 8) or
    ((payload[2].toInt() and 0xFF) shl 16) or
    ((payload[3].toInt() and 0xFF) shl 24)

// Reads one MySQL packet (3-byte little-endian length + 1-byte sequence id + payload), looping over
// partial reads. Throws EOFException if the peer closes before a complete packet.
fun readPacket(input: InputStream, maxPayloadLength: Int = 0xFFFFFF): Pair<Int, ByteArray> {
    val header = ByteArray(4)
    readFully(input, header, 4)
    val length = (header[0].toInt() and 0xFF) or
        ((header[1].toInt() and 0xFF) shl 8) or
        ((header[2].toInt() and 0xFF) shl 16)
    if (length > maxPayloadLength) {
        throw IOException("MySQL packet payload of $length bytes exceeds the allowed $maxPayloadLength")
    }
    val sequenceId = header[3].toInt() and 0xFF
    val payload = ByteArray(length)
    readFully(input, payload, length)
    return Pair(sequenceId, payload)
}

private fun readFully(input: InputStream, buffer: ByteArray, length: Int) {
    var read = 0
    while (read < length) {
        val r = input.read(buffer, read, length - read)
        if (r == -1) throw EOFException("EOF while reading a MySQL packet")
        read += r
    }
}

fun writePacket(output: OutputStream, sequenceId: Int, payload: ByteArray) {
    val header = ByteArray(4)
    val length = payload.size
    header[0] = (length and 0xFF).toByte()
    header[1] = ((length ushr 8) and 0xFF).toByte()
    header[2] = ((length ushr 16) and 0xFF).toByte()
    header[3] = (sequenceId and 0xFF).toByte()
    output.write(header)
    output.write(payload)
    output.flush()
}

fun generateRandomSalt(): ByteArray {
    val random = SecureRandom()
    val salt = ByteArray(20)
    for (i in 0 until 20) {
        // Keep within safe ASCII printable range and non-zero
        salt[i] = (random.nextInt(90) + 33).toByte()
    }
    return salt
}

fun buildInitialHandshake(connectionId: Int, salt: ByteArray, supportSsl: Boolean): ByteArray {
    val serverVersion = "8.0.35-kviklet"
    val bos = ByteArrayOutputStream()
    bos.write(10) // Protocol version
    bos.write(serverVersion.toByteArray(Charsets.US_ASCII))
    bos.write(0) // Null terminator

    // Connection ID
    bos.write(connectionId and 0xFF)
    bos.write((connectionId ushr 8) and 0xFF)
    bos.write((connectionId ushr 16) and 0xFF)
    bos.write((connectionId ushr 24) and 0xFF)

    // Auth-plugin-data-part-1 (8 bytes)
    bos.write(salt, 0, 8)
    bos.write(0) // filler

    // Capability flags lower 2 bytes (0x820c or 0x8a0c)
    // CLIENT_LONG_PASSWORD | CLIENT_FOUND_ROWS | CLIENT_CONNECT_WITH_DB | CLIENT_PROTOCOL_41 | CLIENT_SECURE_CONNECTION
    bos.write(0x0c)
    if (supportSsl) {
        bos.write(0x8a) // 0x8a has CLIENT_SSL set! (0x82 | 0x08)
    } else {
        bos.write(0x82)
    }

    bos.write(45) // Character set: utf8mb4_general_ci

    // Status flags: SERVER_STATUS_AUTOCOMMIT (0x0002)
    bos.write(0x02)
    bos.write(0x00)

    // Capability flags upper 2 bytes (0x0008)
    // CLIENT_PLUGIN_AUTH
    bos.write(0x08)
    bos.write(0x00)

    bos.write(21) // Auth-plugin-data-len

    // Reserved (10 bytes)
    for (i in 0 until 10) bos.write(0)

    // Auth-plugin-data-part-2 (12 bytes)
    bos.write(salt, 8, 12)
    bos.write(0) // filler

    // Auth-plugin-name
    bos.write(NATIVE_PASSWORD_PLUGIN.toByteArray(Charsets.US_ASCII))
    bos.write(0) // Null terminator

    return bos.toByteArray()
}

class HandshakeResponse(
    val username: String,
    val authResponse: ByteArray,
    val database: String?,
    val authPluginName: String?,
) {
    companion object {
        fun parse(payload: ByteArray): HandshakeResponse {
            try {
                val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
                val capabilities = buffer.int
                buffer.int // max packet size
                buffer.get() // charset
                // Skip 23 bytes of reserved/filler
                for (i in 0 until 23) {
                    buffer.get()
                }
                val usernameBytes = ByteArrayOutputStream()
                while (true) {
                    val b = buffer.get()
                    if (b == 0.toByte()) break
                    usernameBytes.write(b.toInt())
                }
                val username = String(usernameBytes.toByteArray(), Charsets.UTF_8)

                // Reject lenenc-encoded auth data -- we don't advertise this capability
                if ((capabilities and 0x00200000) != 0) {
                    throw IOException("CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA is not supported by this proxy")
                }

                val hasSecureConnection = (capabilities and 0x8000) != 0
                val authResponse = if (hasSecureConnection) {
                    val authResponseLen = buffer.get().toInt() and 0xFF
                    val authBytes = ByteArray(authResponseLen)
                    buffer.get(authBytes)
                    authBytes
                } else {
                    val authBytesStream = ByteArrayOutputStream()
                    while (true) {
                        val b = buffer.get()
                        if (b == 0.toByte()) break
                        authBytesStream.write(b.toInt())
                    }
                    authBytesStream.toByteArray()
                }

                var database: String? = null
                if ((capabilities and 0x0008) != 0) {
                    val dbBytes = ByteArrayOutputStream()
                    while (buffer.hasRemaining()) {
                        val b = buffer.get()
                        if (b == 0.toByte()) break
                        dbBytes.write(b.toInt())
                    }
                    database = String(dbBytes.toByteArray(), Charsets.UTF_8)
                }

                var authPluginName: String? = null
                if ((capabilities and 0x00080000) != 0) {
                    val pluginBytes = ByteArrayOutputStream()
                    while (buffer.hasRemaining()) {
                        val b = buffer.get()
                        if (b == 0.toByte()) break
                        pluginBytes.write(b.toInt())
                    }
                    authPluginName = String(pluginBytes.toByteArray(), Charsets.UTF_8)
                }

                return HandshakeResponse(username, authResponse, database, authPluginName)
            } catch (e: BufferUnderflowException) {
                throw IOException("Malformed HandshakeResponse packet (truncated)", e)
            }
        }
    }
}

fun sha1(data: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-1")
    return md.digest(data)
}

fun xor(a: ByteArray, b: ByteArray): ByteArray {
    val result = ByteArray(a.size)
    for (i in a.indices) {
        result[i] = (a[i].toInt() xor b[i].toInt()).toByte()
    }
    return result
}

fun verifyPassword(scramble: ByteArray, password: String, clientHash: ByteArray): Boolean {
    val passwordBytes = password.toByteArray(Charsets.UTF_8)
    val sha1Password = sha1(passwordBytes)
    val sha1Sha1Password = sha1(sha1Password)

    val concat = ByteArray(scramble.size + sha1Sha1Password.size)
    System.arraycopy(scramble, 0, concat, 0, scramble.size)
    System.arraycopy(sha1Sha1Password, 0, concat, scramble.size, sha1Sha1Password.size)

    val sha1Concat = sha1(concat)
    val expectedClientHash = xor(sha1Password, sha1Concat)

    return MessageDigest.isEqual(expectedClientHash, clientHash)
}

fun buildOkPacket(): ByteArray {
    val bos = ByteArrayOutputStream()
    bos.write(0x00) // OK header
    bos.write(0x00) // Affected rows (0)
    bos.write(0x00) // Last insert ID (0)
    bos.write(0x02) // Status flags lower byte (SERVER_STATUS_AUTOCOMMIT)
    bos.write(0x00) // Status flags upper byte
    bos.write(0x00) // Warnings lower byte (0)
    bos.write(0x00) // Warnings upper byte
    return bos.toByteArray()
}

fun buildErrPacket(errorCode: Int, sqlState: String, message: String): ByteArray {
    val bos = ByteArrayOutputStream()
    bos.write(0xFF)
    bos.write(errorCode and 0xFF)
    bos.write((errorCode ushr 8) and 0xFF)
    bos.write('#'.code)
    bos.write(sqlState.toByteArray(Charsets.US_ASCII))
    bos.write(message.toByteArray(Charsets.UTF_8))
    return bos.toByteArray()
}

// AuthSwitchRequest: asks a client that answered for another plugin to redo its response with the native
// scramble over the same salt.
fun buildAuthSwitchRequest(salt: ByteArray): ByteArray {
    val bos = ByteArrayOutputStream()
    bos.write(0xFE)
    bos.write(NATIVE_PASSWORD_PLUGIN.toByteArray(Charsets.US_ASCII))
    bos.write(0)
    bos.write(salt)
    bos.write(0)
    return bos.toByteArray()
}
