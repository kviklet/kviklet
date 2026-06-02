package dev.kviklet.kviklet.proxy.mysql

import dev.kviklet.kviklet.proxy.postgres.TLSCertificate
import dev.kviklet.kviklet.proxy.postgres.enableSSL
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom

fun readPacket(input: InputStream): Pair<Byte, ByteArray> {
    val header = ByteArray(4)
    var read = 0
    while (read < 4) {
        val r = input.read(header, read, 4 - read)
        if (r == -1) throw EOFException("EOF while reading packet header")
        read += r
    }
    val length = (header[0].toInt() and 0xFF) or
                 ((header[1].toInt() and 0xFF) shl 8) or
                 ((header[2].toInt() and 0xFF) shl 16)
    val sequenceId = header[3]
    val payload = ByteArray(length)
    read = 0
    while (read < length) {
        val r = input.read(payload, read, length - read)
        if (r == -1) throw EOFException("EOF while reading packet payload")
        read += r
    }
    return Pair(sequenceId, payload)
}

fun writePacket(output: OutputStream, sequenceId: Byte, payload: ByteArray) {
    val header = ByteArray(4)
    val length = payload.size
    header[0] = (length and 0xFF).toByte()
    header[1] = ((length ushr 8) and 0xFF).toByte()
    header[2] = ((length ushr 16) and 0xFF).toByte()
    header[3] = sequenceId
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
    bos.write("mysql_native_password".toByteArray(Charsets.US_ASCII))
    bos.write(0) // Null terminator
    
    return bos.toByteArray()
}

class HandshakeResponse(
    val username: String,
    val authResponse: ByteArray,
    val database: String?,
    val authPluginName: String?
) {
    companion object {
        fun parse(payload: ByteArray): HandshakeResponse {
            val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
            val capabilities = buffer.int
            val maxPacketSize = buffer.int
            val charset = buffer.get()
            // Skip 23 bytes of reserved/filler
            for (i in 0 until 23) {
                buffer.get()
            }
            // Read null-terminated username
            val usernameBytes = ByteArrayOutputStream()
            while (true) {
                val b = buffer.get()
                if (b == 0.toByte()) break
                usernameBytes.write(b.toInt())
            }
            val username = String(usernameBytes.toByteArray(), Charsets.UTF_8)
            
            // Read auth response
            var authResponseLen = 0
            val hasSecureConnection = (capabilities and 0x8000) != 0
            val authResponse = if (hasSecureConnection) {
                authResponseLen = buffer.get().toInt() and 0xFF
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
    
    return expectedClientHash.contentEquals(clientHash)
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

fun setupClientMySql(
    client: Socket,
    tlsCert: TLSCertificate?,
    username: String,
    password: String,
): Socket {
    var input = client.getInputStream()
    var output = client.getOutputStream()
    var finalSocket: Socket = client

    // 1. Send Initial Handshake
    val salt = generateRandomSalt()
    val handshakePayload = buildInitialHandshake(1, salt, tlsCert != null)
    writePacket(output, 0, handshakePayload)

    // 2. Read Client Handshake Response
    var (seqId, payload) = readPacket(input)

    // 3. Check for SSL Request
    if (payload.size >= 4) {
        val capabilities = (payload[0].toInt() and 0xFF) or
                           ((payload[1].toInt() and 0xFF) shl 8) or
                           ((payload[2].toInt() and 0xFF) shl 16) or
                           ((payload[3].toInt() and 0xFF) shl 24)
        if ((capabilities and 0x0800) != 0) { // CLIENT_SSL
            if (tlsCert == null) {
                val errPayload = buildErrPacket(2000, "HY000", "SSL connection requested but SSL is not supported by proxy")
                writePacket(output, (seqId + 1).toByte(), errPayload)
                throw IOException("Client requested SSL but SSL is not supported by proxy")
            }
            finalSocket = enableSSL(client, tlsCert)
            input = finalSocket.getInputStream()
            output = finalSocket.getOutputStream()

            // Read the actual Handshake Response from the encrypted stream
            val nextPacket = readPacket(input)
            seqId = nextPacket.first
            payload = nextPacket.second
        }
    }

    // 4. Parse Handshake Response
    println("Parsing Handshake Response. Payload size: ${payload.size}")
    val response = HandshakeResponse.parse(payload)
    println("Parsed Handshake Response. Username: '${response.username}', Database: '${response.database}', Plugin: '${response.authPluginName}'")

    // 5. Verify username and password (indistinguishable paths to prevent username enumeration and timing attacks)
    val isUsernameValid = (response.username == username)
    val isPasswordValid = verifyPassword(salt, password, response.authResponse)

    if (!isUsernameValid || !isPasswordValid) {
        println("Authentication failed for user '${response.username}'")
        val errPayload = buildErrPacket(1045, "28000", "Access denied for user '${response.username}' (using password: YES)")
        writePacket(output, (seqId + 1).toByte(), errPayload)
        throw IOException("Authentication failed: Access denied for user '${response.username}'")
    }

    println("Authentication successful! Sending OK packet...")
    // 6. Send OK packet
    val okPayload = buildOkPacket()
    writePacket(output, (seqId + 1).toByte(), okPayload)
    println("OK packet sent with sequence ID: ${(seqId + 1)}")

    return finalSocket
}
