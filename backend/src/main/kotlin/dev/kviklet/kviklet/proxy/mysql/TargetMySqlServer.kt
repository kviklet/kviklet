package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Socket

class TargetMySqlConnection(val socket: Socket)

class TargetMySqlSocketFactory(
    private val datasourceType: DatasourceType,
    private val authenticationDetails: AuthenticationDetails.UserPassword,
    private val databaseName: String,
    private val targetHost: String,
    private val targetPort: Int,
) {
    fun createTargetMySqlConnection(): TargetMySqlConnection {
        val socket = Socket(targetHost, targetPort)
        try {
            performHandshake(socket)
            return TargetMySqlConnection(socket)
        } catch (e: Exception) {
            socket.close()
            throw e
        }
    }

    private fun performHandshake(socket: Socket) {
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        // Read server Initial Handshake (Protocol Version 10)
        val (seqId, handshake) = readPacket(input)
        if (handshake.isEmpty() || handshake[0] != 10.toByte()) {
            throw IOException("Unsupported MySQL protocol version (expected 10, got ${handshake.getOrNull(0)})")
        }

        val salt = parseSalt(handshake)
        val authToken = computeNativePasswordToken(authenticationDetails.password, salt)
        val response = buildHandshakeResponse(authenticationDetails.username, authToken, databaseName)
        writePacket(output, (seqId.toInt() + 1).toByte(), response)

        val (_, reply) = readPacket(input)
        if (reply.isEmpty()) throw IOException("Empty response from MySQL server during authentication")
        when (reply[0].toInt() and 0xFF) {
            0x00 -> return // OK
            0xFF -> {
                val msg = if (reply.size > 9) String(reply, 9, reply.size - 9, Charsets.UTF_8) else "unknown error"
                throw IOException("MySQL server rejected credentials: $msg")
            }
            0xFE -> throw IOException("Auth-switch-request from the target server is not supported")
            else -> throw IOException("Unexpected server response byte: 0x${(reply[0].toInt() and 0xFF).toString(16)}")
        }
    }

    // MySQL Protocol v10 Initial Handshake packet layout:
    //   1  protocol version
    //   N+1  server version (null-terminated)
    //   4  connection ID
    //   8  auth-plugin-data-part-1
    //   1  filler
    //   2  capability flags lower
    //   1  charset
    //   2  status flags
    //   2  capability flags upper
    //   1  auth-plugin-data-len
    //   10 reserved
    //   12 auth-plugin-data-part-2
    private fun parseSalt(handshake: ByteArray): ByteArray {
        var pos = 1
        while (pos < handshake.size && handshake[pos] != 0.toByte()) pos++
        pos++ // skip null terminator of server version
        pos += 4 // skip connection ID
        val salt = ByteArray(20)
        System.arraycopy(handshake, pos, salt, 0, 8)
        pos += 8 + 1 + 2 + 1 + 2 + 2 + 1 + 10 // filler + cap_lo + charset + status + cap_hi + salt_len + reserved
        System.arraycopy(handshake, pos, salt, 8, 12)
        return salt
    }

    private fun computeNativePasswordToken(password: String, salt: ByteArray): ByteArray {
        if (password.isEmpty()) return ByteArray(0)
        val sha1Password = sha1(password.toByteArray(Charsets.UTF_8))
        val sha1Sha1Password = sha1(sha1Password)
        return xor(sha1Password, sha1(salt + sha1Sha1Password))
    }

    private fun buildHandshakeResponse(username: String, authToken: ByteArray, database: String): ByteArray {
        val bos = ByteArrayOutputStream()

        // CLIENT_PROTOCOL_41 | CLIENT_SECURE_CONNECTION | CLIENT_PLUGIN_AUTH | CLIENT_LONG_FLAG | CLIENT_TRANSACTIONS
        var caps = 0x0200 or 0x8000 or 0x00080000 or 0x0004 or 0x2000
        if (database.isNotEmpty()) caps = caps or 0x0008 // CLIENT_CONNECT_WITH_DB

        bos.write(caps and 0xFF)
        bos.write((caps ushr 8) and 0xFF)
        bos.write((caps ushr 16) and 0xFF)
        bos.write((caps ushr 24) and 0xFF)
        // Max packet size (16 MB)
        bos.write(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0x00))
        // Character set (utf8mb4_general_ci = 45)
        bos.write(45)
        // 23 reserved bytes
        repeat(23) { bos.write(0) }
        // Username (null-terminated)
        bos.write(username.toByteArray(Charsets.UTF_8))
        bos.write(0)
        // Auth token (length-prefixed, CLIENT_SECURE_CONNECTION style)
        bos.write(authToken.size)
        if (authToken.isNotEmpty()) bos.write(authToken)
        // Database (null-terminated, only when CLIENT_CONNECT_WITH_DB is set)
        if (database.isNotEmpty()) {
            bos.write(database.toByteArray(Charsets.UTF_8))
            bos.write(0)
        }
        // Auth plugin name (null-terminated)
        bos.write("mysql_native_password".toByteArray(Charsets.US_ASCII))
        bos.write(0)

        return bos.toByteArray()
    }
}
