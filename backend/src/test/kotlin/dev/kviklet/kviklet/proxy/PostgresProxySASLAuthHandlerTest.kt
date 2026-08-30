// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.SASLAuthHandler
import dev.kviklet.kviklet.proxy.postgres.hmacSha256
import dev.kviklet.kviklet.proxy.postgres.pbkdf2
import dev.kviklet.kviklet.proxy.postgres.sha256
import dev.kviklet.kviklet.proxy.postgres.xorBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.Base64

// KVI-247 #2: the SASL exchange must not assume one read() delivers one whole frame. TCP can split a
// valid login's SASL messages across segments, so the handler has to accumulate until the declared frame
// length is satisfied -- the scripted client below delivers ONE byte per read to force the worst case.
class PostgresProxySASLAuthHandlerTest {

    // A complete SCRAM-SHA-256 client speaking against the handler's output buffer. It serves its two
    // messages (SASLInitialResponse, then the client-final computed from the server-first the handler
    // wrote) one byte per read() call, simulating maximal TCP fragmentation.
    private class OneByteAtATimeScramClient(
        private val password: String,
        private val serverOutput: ByteArrayOutputStream,
        private val clientNonce: String = "clientNonceABC123",
    ) : InputStream() {
        private var pending = clientFirstMessage()
        private var offset = 0
        private var finalSent = false

        override fun read(): Int {
            if (offset >= pending.size) {
                if (finalSent) return -1
                pending = clientFinalMessage()
                offset = 0
                finalSent = true
            }
            return pending[offset++].toInt() and 0xFF
        }

        // Deliver at most one byte no matter how large a buffer is offered.
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val value = read()
            if (value == -1) return -1
            b[off] = value.toByte()
            return 1
        }

        private fun clientFirstMessage(): ByteArray {
            val saslData = "n,,n=,r=$clientNonce".toByteArray(Charsets.UTF_8)
            val body = ByteArrayOutputStream()
            body.write("SCRAM-SHA-256".toByteArray(Charsets.UTF_8))
            body.write(0)
            body.write(ByteBuffer.allocate(4).putInt(saslData.size).array())
            body.write(saslData)
            return saslMessage(body.toByteArray())
        }

        // By the time the client-first bytes are exhausted the handler has written its
        // AuthenticationSASLContinue, so the server-first can be read back out of the output buffer.
        private fun clientFinalMessage(): ByteArray {
            val written = serverOutput.toByteArray()
            assertEquals('R'.code.toByte(), written[0], "expected an authentication message from the handler")
            assertEquals(11, ByteBuffer.wrap(written, 5, 4).int, "expected an AuthenticationSASLContinue")
            val serverFirst = String(written, 9, written.size - 9, Charsets.UTF_8)

            val fields = serverFirst.split(',').associate { it.substringBefore('=') to it.substringAfter('=') }
            val combinedNonce = fields.getValue("r")
            val salt = Base64.getDecoder().decode(fields.getValue("s"))
            val iterations = fields.getValue("i").toInt()

            val withoutProof = "c=biws,r=$combinedNonce"
            val authMessage = "n=,r=$clientNonce,$serverFirst,$withoutProof"
            val saltedPassword = pbkdf2(password, salt, iterations)
            val clientKey = hmacSha256(saltedPassword, "Client Key")
            val storedKey = sha256(clientKey)
            val clientSignature = hmacSha256(storedKey, authMessage)
            val proof = Base64.getEncoder().encodeToString(xorBytes(clientKey, clientSignature))
            return saslMessage("$withoutProof,p=$proof".toByteArray(Charsets.UTF_8))
        }

        private fun saslMessage(body: ByteArray): ByteArray {
            val message = ByteBuffer.allocate(5 + body.size)
            message.put('p'.code.toByte())
            message.putInt(4 + body.size)
            message.put(body)
            return message.array()
        }
    }

    @Test
    fun `a valid SASL exchange split into one-byte reads completes successfully`() {
        val output = ByteArrayOutputStream()
        val client = OneByteAtATimeScramClient("s3cretPassword", output)
        val handler = SASLAuthHandler(output, client, "s3cretPassword", isUserValid = true)

        handler.handle()

        // After the continue message the handler must have written an AuthenticationSASLFinal.
        val written = output.toByteArray()
        val continueLength = 1 + ByteBuffer.wrap(written, 1, 4).int
        assertTrue(written.size > continueLength, "expected a second message after the SASLContinue")
        assertEquals('R'.code.toByte(), written[continueLength])
        assertEquals(12, ByteBuffer.wrap(written, continueLength + 5, 4).int, "expected an AuthenticationSASLFinal")
    }

    @Test
    fun `a wrong password still fails with a 28P01 error when the exchange is split into one-byte reads`() {
        val output = ByteArrayOutputStream()
        val client = OneByteAtATimeScramClient("wrongPassword", output)
        val handler = SASLAuthHandler(output, client, "actualPassword", isUserValid = true)

        val thrown = assertThrows(Exception::class.java) { handler.handle() }

        assertTrue(
            thrown.message?.contains("Authentication failed") == true,
            "expected an authentication failure, got: ${thrown::class.simpleName}: ${thrown.message}",
        )
        // The last message written must be the ErrorResponse, not a bare connection reset.
        val written = output.toByteArray()
        val continueLength = 1 + ByteBuffer.wrap(written, 1, 4).int
        assertEquals('E'.code.toByte(), written[continueLength], "expected an ErrorResponse after the proof")
        assertTrue(String(written, Charsets.ISO_8859_1).contains("28P01"))
    }
}
