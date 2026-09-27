// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.core.ProxySession
import dev.kviklet.kviklet.proxy.mysql.HandshakeResponse
import dev.kviklet.kviklet.proxy.mysql.authenticateClientMySql
import dev.kviklet.kviklet.proxy.mysql.readPacket
import dev.kviklet.kviklet.proxy.mysql.sha1
import dev.kviklet.kviklet.proxy.mysql.writePacket
import dev.kviklet.kviklet.proxy.mysql.xor
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// The client-facing handshake against hand-built HandshakeResponse packets laid out the way libmysqlclient
// (the mysql CLI) and PyMySQL send them: both set capability flags the proxy never advertised
// (CLIENT_LOCAL_FILES, CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA), which the JDBC drivers the other proxy tests
// use never do.
class MySqlProxyHandshakeCapabilitiesTest {
    companion object {
        private const val CLIENT_LONG_PASSWORD = 0x0001
        private const val CLIENT_CONNECT_WITH_DB = 0x0008
        private const val CLIENT_COMPRESS = 0x0020
        private const val CLIENT_LOCAL_FILES = 0x0080
        private const val CLIENT_PROTOCOL_41 = 0x0200
        private const val CLIENT_SECURE_CONNECTION = 0x8000
        private const val CLIENT_PLUGIN_AUTH = 0x00080000
        private const val CLIENT_CONNECT_ATTRS = 0x00100000
        private const val CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA = 0x00200000

        private const val BASE_CAPABILITIES = CLIENT_LONG_PASSWORD or CLIENT_CONNECT_WITH_DB or
            CLIENT_PROTOCOL_41 or CLIENT_SECURE_CONNECTION or CLIENT_PLUGIN_AUTH

        private const val PASSWORD = "proxyPassword"
    }

    private fun writeInt32(bos: ByteArrayOutputStream, value: Int) {
        bos.write(value and 0xFF)
        bos.write((value ushr 8) and 0xFF)
        bos.write((value ushr 16) and 0xFF)
        bos.write((value ushr 24) and 0xFF)
    }

    private fun writeLengthEncoded(bos: ByteArrayOutputStream, value: Int) {
        when {
            value < 0xFB -> bos.write(value)

            value < 0x10000 -> {
                bos.write(0xFC)
                bos.write(value and 0xFF)
                bos.write((value ushr 8) and 0xFF)
            }

            else -> throw IllegalArgumentException("Not needed by these tests")
        }
    }

    // A HandshakeResponse41 payload in the layout every protocol-4.1 client uses; the auth response form
    // follows the capability flags exactly as a real client's would.
    private fun handshakeResponse(
        capabilities: Int,
        username: String = "proxyUser",
        authResponse: ByteArray,
        database: String? = "testdb",
        pluginName: String? = "mysql_native_password",
    ): ByteArray {
        val bos = ByteArrayOutputStream()
        writeInt32(bos, capabilities)
        writeInt32(bos, 16777216) // max packet size
        bos.write(45) // utf8mb4_general_ci
        repeat(23) { bos.write(0) }
        bos.write(username.toByteArray(Charsets.UTF_8))
        bos.write(0)
        when {
            (capabilities and CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA) != 0 -> {
                writeLengthEncoded(bos, authResponse.size)
                bos.write(authResponse)
            }

            (capabilities and CLIENT_SECURE_CONNECTION) != 0 -> {
                bos.write(authResponse.size)
                bos.write(authResponse)
            }

            else -> {
                bos.write(authResponse)
                bos.write(0)
            }
        }
        if ((capabilities and CLIENT_CONNECT_WITH_DB) != 0 && database != null) {
            bos.write(database.toByteArray(Charsets.UTF_8))
            bos.write(0)
        }
        if ((capabilities and CLIENT_PLUGIN_AUTH) != 0 && pluginName != null) {
            bos.write(pluginName.toByteArray(Charsets.UTF_8))
            bos.write(0)
        }
        if ((capabilities and CLIENT_CONNECT_ATTRS) != 0) {
            // One attribute, "program_name" = "mysql", as a lenenc-prefixed block of lenenc strings.
            val attrs = ByteArrayOutputStream()
            for (s in listOf("program_name", "mysql")) {
                attrs.write(s.length)
                attrs.write(s.toByteArray(Charsets.US_ASCII))
            }
            writeLengthEncoded(bos, attrs.size())
            bos.write(attrs.toByteArray())
        }
        return bos.toByteArray()
    }

    @Test
    fun `a length-encoded auth response is parsed like the one-byte-length form`() {
        val auth = ByteArray(20) { (it + 1).toByte() }
        val payload = handshakeResponse(
            BASE_CAPABILITIES or CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA or CLIENT_CONNECT_ATTRS,
            authResponse = auth,
        )

        val response = HandshakeResponse.parse(payload)

        assertEquals("proxyUser", response.username)
        assertArrayEquals(auth, response.authResponse)
        assertEquals("testdb", response.database)
        assertEquals("mysql_native_password", response.authPluginName)
    }

    @Test
    fun `a multi-byte length-encoded auth response is decoded, not read as a one-byte length`() {
        // 300 bytes needs the 0xFC two-byte length prefix; a one-byte-length reader would misparse it.
        val auth = ByteArray(300) { (it % 251).toByte() }
        val payload = handshakeResponse(
            BASE_CAPABILITIES or CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA,
            authResponse = auth,
        )

        val response = HandshakeResponse.parse(payload)

        assertArrayEquals(auth, response.authResponse)
        assertEquals("testdb", response.database)
        assertEquals("mysql_native_password", response.authPluginName)
    }

    @Test
    fun `the one-byte-length auth response form still parses without the lenenc flag`() {
        val auth = ByteArray(20) { (it + 1).toByte() }
        val response = HandshakeResponse.parse(handshakeResponse(BASE_CAPABILITIES, authResponse = auth))

        assertArrayEquals(auth, response.authResponse)
        assertEquals("mysql_native_password", response.authPluginName)
    }

    @Test
    fun `a length-encoded auth response longer than the packet is rejected as malformed`() {
        val payload = handshakeResponse(
            BASE_CAPABILITIES or CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA,
            authResponse = ByteArray(20),
            database = null,
            pluginName = null,
        )
        // Claim 200 auth bytes where only 20 follow.
        val usernameEnd = 4 + 4 + 1 + 23 + "proxyUser".length + 1
        payload[usernameEnd] = 200.toByte()

        val error = assertThrows<IOException> { HandshakeResponse.parse(payload) }
        assertTrue(error.message!!.contains("Malformed"), error.message)
    }

    // --- The full client-facing handshake over a socket pair -------------------------------------------

    private class HandshakeRun(val serverResponse: ByteArray, val serverOutcome: Result<Any?>)

    // Runs authenticateClientMySql on one end of a socket pair while acting as the client on the other:
    // reads the initial handshake, answers with the given HandshakeResponse (the auth response computed
    // from the real salt), and returns both the packet the client got back and how the server side ended.
    private fun runHandshake(
        capabilities: Int,
        buildResponse: (capabilities: Int, scramble: ByteArray) -> ByteArray,
    ): HandshakeRun {
        val session = mockk<ProxySession>(relaxed = true)
        every { session.password } returns PASSWORD
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0).use { listener ->
                Socket("localhost", listener.localPort).use { client ->
                    val serverSide = listener.accept()
                    serverSide.soTimeout = 5_000
                    val server = executor.submit<Any?> {
                        authenticateClientMySql(serverSide, null, "unknownUserPassword") { username ->
                            session.takeIf { username == "proxyUser" }
                        }
                    }

                    client.soTimeout = 5_000
                    val input = client.getInputStream()
                    val (_, initialHandshake) = readPacket(input)
                    val salt = saltOf(initialHandshake)
                    val scramble = xor(sha1(PASSWORD.toByteArray()), sha1(salt + sha1(sha1(PASSWORD.toByteArray()))))
                    writePacket(client.getOutputStream(), 1, buildResponse(capabilities, scramble))
                    val (_, reply) = readPacket(input)
                    val outcome = runCatching { server.get(5, TimeUnit.SECONDS) }
                    return HandshakeRun(reply, outcome)
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    // The 20-byte scramble split across auth-plugin-data-part-1 (8 bytes) and part-2 (12 bytes) of the
    // initial handshake packet.
    private fun saltOf(initialHandshake: ByteArray): ByteArray {
        var i = 1 // skip the protocol version
        while (initialHandshake[i] != 0.toByte()) i++ // server version, NUL-terminated
        i += 1 + 4 // NUL + connection id
        val part1 = initialHandshake.copyOfRange(i, i + 8)
        i += 8 + 1 + 2 + 1 + 2 + 2 + 1 + 10 // filler, caps low, charset, status, caps high, auth len, reserved
        val part2 = initialHandshake.copyOfRange(i, i + 12)
        return part1 + part2
    }

    @Test
    fun `a client advertising LOCAL_FILES and lenenc auth data, like the mysql CLI, authenticates`() {
        val run = runHandshake(
            BASE_CAPABILITIES or CLIENT_LOCAL_FILES or CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA or CLIENT_CONNECT_ATTRS,
        ) { caps, scramble -> handshakeResponse(caps, authResponse = scramble) }

        assertEquals(0x00, run.serverResponse[0].toInt() and 0xFF, "expected an OK packet")
        assertNotNull(run.serverOutcome.getOrThrow(), "the server side must hand back an authenticated client")
    }

    @Test
    fun `a wrong password from a client using lenenc auth data is still refused`() {
        val run = runHandshake(
            BASE_CAPABILITIES or CLIENT_LOCAL_FILES or CLIENT_PLUGIN_AUTH_LENENC_CLIENT_DATA,
        ) { caps, scramble -> handshakeResponse(caps, authResponse = scramble.also { it[0] = (it[0] + 1).toByte() }) }

        assertEquals(0xFF, run.serverResponse[0].toInt() and 0xFF, "expected an ERR packet")
        assertTrue(run.serverOutcome.isFailure)
    }

    @Test
    fun `a client advertising COMPRESS is still refused up front`() {
        val run = runHandshake(BASE_CAPABILITIES or CLIENT_COMPRESS) { caps, scramble ->
            handshakeResponse(caps, authResponse = scramble)
        }

        assertEquals(0xFF, run.serverResponse[0].toInt() and 0xFF, "expected an ERR packet")
        val message = String(run.serverResponse, 9, run.serverResponse.size - 9, Charsets.UTF_8)
        assertTrue(message.contains("COMPRESS"), message)
        assertTrue(run.serverOutcome.isFailure)
    }
}
