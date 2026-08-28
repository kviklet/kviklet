// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.messages.MessageFramer
import dev.kviklet.kviklet.proxy.postgres.messages.SASLInitialResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

// SASLInitialResponse.fromBytes must follow the same (declaredLength, body) contract that the relay
// MessageFramer uses. Previously the auth path passed the raw read plus the whole buffer while the framer
// passed the declared length plus the length-4 body, so any 'p' message reaching the framer built a
// ByteArray(length) and overran the shorter body -> BufferUnderflowException killing the session.
class PostgresProxySASLParsingTest {

    // Builds a full SASLInitialResponse wire message: 'p', int32 length, mechanism-name cstring,
    // int32 SASL-data length, then the SASL data (gs2 header + client-first-bare).
    private fun saslInitialResponse(clientNonce: String): ByteArray {
        val saslData = "n,,n=,r=$clientNonce".toByteArray(Charsets.UTF_8)
        val body = ByteArrayOutputStream()
        body.write("SCRAM-SHA-256".toByteArray(Charsets.UTF_8))
        body.write(0)
        body.write(ByteBuffer.allocate(4).putInt(saslData.size).array())
        body.write(saslData)
        val bodyBytes = body.toByteArray()

        val length = 4 + bodyBytes.size
        val message = ByteBuffer.allocate(1 + length)
        message.put('p'.code.toByte())
        message.putInt(length)
        message.put(bodyBytes)
        return message.array()
    }

    // Builds a SASLInitialResponse body (as the framer delivers it: header and length already stripped)
    // whose declared SASL-data length can be set independently of how many data bytes are actually present.
    private fun saslInitialResponseBody(declaredSaslDataLength: Int, presentData: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("SCRAM-SHA-256".toByteArray(Charsets.UTF_8))
        body.write(0)
        body.write(ByteBuffer.allocate(4).putInt(declaredSaslDataLength).array())
        body.write(presentData)
        return body.toByteArray()
    }

    @Test
    fun `a SASL data length larger than the message is rejected instead of allocating for it`() {
        // Only 3 data bytes are present but the packet declares far more. Without a bound check the parser
        // allocates ByteArray(declared) off an unauthenticated packet (a pre-auth heap-pressure DoS); a huge
        // declared value would even throw OutOfMemoryError, which slips past the catch(Exception) in the path.
        val body = saslInitialResponseBody(declaredSaslDataLength = 50, presentData = "n,,".toByteArray())

        val thrown = assertThrows(Exception::class.java) {
            SASLInitialResponse.fromBytes(4 + body.size, body)
        }
        assertTrue(
            thrown.message?.contains("SASL data length") == true,
            "expected a controlled length-validation error, got: ${thrown::class.simpleName}: ${thrown.message}",
        )
    }

    @Test
    fun `a huge declared SASL data length is rejected without allocating`() {
        // 0x30000000 (~768MB) would be allocated and zeroed before the follow-up read fails, if unguarded.
        val body = saslInitialResponseBody(declaredSaslDataLength = 0x30000000, presentData = "n,,".toByteArray())

        assertThrows(Exception::class.java) {
            SASLInitialResponse.fromBytes(4 + body.size, body)
        }
    }

    @Test
    fun `a SASLInitialResponse is parsed through the framer without overrunning the body`() {
        val message = saslInitialResponse("clientNonceABC123")

        val messages = MessageFramer().feed(message)

        assertEquals(1, messages.size)
        val parsed = assertInstanceOf(SASLInitialResponse::class.java, messages[0])
        assertEquals("clientNonceABC123", parsed.getClientNonce())
    }
}
