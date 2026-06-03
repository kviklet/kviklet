package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.service.dto.AuthenticationDetails
import dev.kviklet.kviklet.service.dto.DatasourceType
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher

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

        // Read the server's Initial Handshake (Protocol Version 10)
        val (handshakeSeq, handshake) = readPacket(input)
        if (handshake.isEmpty() || handshake[0] != 10.toByte()) {
            throw IOException("Unsupported MySQL protocol version (expected 10, got ${handshake.getOrNull(0)})")
        }

        val server = parseServerHandshake(handshake)
        var plugin = server.authPlugin
        var nonce = server.nonce

        // Send the HandshakeResponse using the plugin the server advertised
        var seq = handshakeSeq.toInt()
        val authToken = computeAuthToken(plugin, authenticationDetails.password, nonce)
        val response = buildHandshakeResponse(authenticationDetails.username, authToken, databaseName, plugin)
        seq += 1
        writePacket(output, seq.toByte(), response)

        // Drive the post-handshake exchange until we reach an OK or ERR packet
        while (true) {
            val (replySeq, reply) = readPacket(input)
            seq = replySeq.toInt()
            if (reply.isEmpty()) throw IOException("Empty response from MySQL server during authentication")

            when (reply[0].toInt() and 0xFF) {
                0x00 -> return // OK — authenticated
                0xFF -> throw IOException("MySQL server rejected credentials: ${parseErrMessage(reply)}")
                0xFE -> {
                    // AuthSwitchRequest: 0xFE + plugin name (null-terminated) + auth data
                    val (newPlugin, newNonce) = parseAuthSwitch(reply)
                    plugin = newPlugin
                    nonce = newNonce
                    seq += 1
                    writePacket(output, seq.toByte(), computeAuthToken(plugin, authenticationDetails.password, nonce))
                }
                0x01 -> {
                    // AuthMoreData — currently only used by caching_sha2_password
                    if (plugin != "caching_sha2_password") {
                        throw IOException("Unexpected AuthMoreData for auth plugin '$plugin'")
                    }
                    when (reply.getOrNull(1)?.toInt()?.and(0xFF)) {
                        0x03 -> { /* fast_auth_success — server will send OK next */ }
                        0x04 -> {
                            // perform_full_authentication: over a plaintext socket we must
                            // request the server's RSA public key and send the encrypted password.
                            seq += 1
                            writePacket(output, seq.toByte(), byteArrayOf(0x02)) // request_public_key
                            val (keySeq, keyReply) = readPacket(input)
                            seq = keySeq.toInt()
                            if (keyReply.isEmpty() || (keyReply[0].toInt() and 0xFF) != 0x01) {
                                throw IOException("Expected RSA public key from server, got 0x${(keyReply.getOrNull(0)?.toInt()?.and(0xFF) ?: -1).toString(16)}")
                            }
                            val pem = String(keyReply, 1, keyReply.size - 1, Charsets.US_ASCII)
                            val encrypted = encryptPasswordRsa(authenticationDetails.password, nonce, pem)
                            seq += 1
                            writePacket(output, seq.toByte(), encrypted)
                        }
                        else -> throw IOException(
                            "Unexpected caching_sha2_password more-data byte: " +
                                "0x${(reply.getOrNull(1)?.toInt()?.and(0xFF) ?: -1).toString(16)}",
                        )
                    }
                }
                else -> throw IOException(
                    "Unexpected server response byte: 0x${(reply[0].toInt() and 0xFF).toString(16)}",
                )
            }
        }
    }

    private data class ServerHandshake(val nonce: ByteArray, val authPlugin: String)

    // MySQL Protocol v10 Initial Handshake layout:
    //   1  protocol version
    //   N+1  server version (null-terminated)
    //   4  connection id
    //   8  auth-plugin-data-part-1
    //   1  filler
    //   2  capability flags (lower)
    //   1  charset
    //   2  status flags
    //   2  capability flags (upper)
    //   1  auth-plugin-data length
    //   10 reserved
    //   [CLIENT_SECURE_CONNECTION] auth-plugin-data-part-2 (max(13, len-8) bytes)
    //   [CLIENT_PLUGIN_AUTH] auth plugin name (null-terminated)
    private fun parseServerHandshake(handshake: ByteArray): ServerHandshake {
        var pos = 1
        while (pos < handshake.size && handshake[pos] != 0.toByte()) pos++
        pos++ // null terminator of server version
        pos += 4 // connection id
        val saltPart1 = handshake.copyOfRange(pos, pos + 8)
        pos += 8
        pos += 1 // filler
        val capLower = (handshake[pos].toInt() and 0xFF) or ((handshake[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        pos += 1 // charset
        pos += 2 // status flags
        val capUpper = (handshake[pos].toInt() and 0xFF) or ((handshake[pos + 1].toInt() and 0xFF) shl 8)
        pos += 2
        val capabilities = capLower or (capUpper shl 16)
        val authPluginDataLen = handshake[pos].toInt() and 0xFF
        pos += 1
        pos += 10 // reserved

        var saltPart2 = ByteArray(0)
        if ((capabilities and 0x8000) != 0) { // CLIENT_SECURE_CONNECTION
            val len2 = maxOf(13, authPluginDataLen - 8)
            saltPart2 = handshake.copyOfRange(pos, pos + minOf(12, len2)) // first 12 bytes are the nonce tail
            pos += len2
        }

        var plugin = "mysql_native_password"
        if ((capabilities and 0x00080000) != 0) { // CLIENT_PLUGIN_AUTH
            val sb = ByteArrayOutputStream()
            while (pos < handshake.size && handshake[pos] != 0.toByte()) {
                sb.write(handshake[pos].toInt())
                pos++
            }
            plugin = String(sb.toByteArray(), Charsets.US_ASCII)
        }

        return ServerHandshake(saltPart1 + saltPart2, plugin)
    }

    private fun parseAuthSwitch(packet: ByteArray): Pair<String, ByteArray> {
        var pos = 1 // skip 0xFE
        val sb = ByteArrayOutputStream()
        while (pos < packet.size && packet[pos] != 0.toByte()) {
            sb.write(packet[pos].toInt())
            pos++
        }
        pos++ // null terminator
        val plugin = String(sb.toByteArray(), Charsets.US_ASCII)
        // Remaining auth data; trim a single trailing null if present
        var end = packet.size
        if (end > pos && packet[end - 1] == 0.toByte()) end--
        val nonce = packet.copyOfRange(pos, end)
        return plugin to nonce
    }

    private fun parseErrMessage(packet: ByteArray): String {
        // 0xFF + 2-byte error code + (optional '#' + 5-byte sqlstate) + message
        if (packet.size <= 3) return "unknown error"
        var pos = 3
        if (packet.size > 3 && packet[3] == '#'.code.toByte()) pos = 9
        if (pos >= packet.size) return "unknown error"
        return String(packet, pos, packet.size - pos, Charsets.UTF_8)
    }

    private fun computeAuthToken(plugin: String, password: String, nonce: ByteArray): ByteArray = when (plugin) {
        "mysql_native_password" -> computeNativePasswordToken(password, nonce)
        "caching_sha2_password" -> computeCachingSha2Token(password, nonce)
        else -> throw IOException("Unsupported target auth plugin '$plugin'")
    }

    private fun computeNativePasswordToken(password: String, nonce: ByteArray): ByteArray {
        if (password.isEmpty()) return ByteArray(0)
        val sha1Password = sha1(password.toByteArray(Charsets.UTF_8))
        val sha1Sha1Password = sha1(sha1Password)
        return xor(sha1Password, sha1(nonce + sha1Sha1Password))
    }

    // caching_sha2_password scramble: SHA256(pw) XOR SHA256(SHA256(SHA256(pw)) || nonce)
    private fun computeCachingSha2Token(password: String, nonce: ByteArray): ByteArray {
        if (password.isEmpty()) return ByteArray(0)
        val h1 = sha256(password.toByteArray(Charsets.UTF_8))
        val h2 = sha256(h1)
        val h3 = sha256(h2 + nonce)
        return xor(h1, h3)
    }

    private fun encryptPasswordRsa(password: String, nonce: ByteArray, pemPublicKey: String): ByteArray {
        // Obfuscate (password + null terminator) by XOR with the nonce cycled over its length
        val pw = password.toByteArray(Charsets.UTF_8) + byteArrayOf(0)
        val obfuscated = ByteArray(pw.size)
        for (i in pw.indices) {
            obfuscated[i] = (pw[i].toInt() xor nonce[i % nonce.size].toInt()).toByte()
        }
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, parsePublicKey(pemPublicKey))
        return cipher.doFinal(obfuscated)
    }

    private fun parsePublicKey(pem: String): java.security.PublicKey {
        val base64 = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")
        val der = Base64.getDecoder().decode(base64)
        return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
    }

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun buildHandshakeResponse(
        username: String,
        authToken: ByteArray,
        database: String,
        authPlugin: String,
    ): ByteArray {
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
        bos.write(authPlugin.toByteArray(Charsets.US_ASCII))
        bos.write(0)

        return bos.toByteArray()
    }
}
