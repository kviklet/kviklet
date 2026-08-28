// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.messages.MessageFramer
import dev.kviklet.kviklet.proxy.postgres.messages.SASLInitialResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
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

    @Test
    fun `a SASLInitialResponse is parsed through the framer without overrunning the body`() {
        val message = saslInitialResponse("clientNonceABC123")

        val messages = MessageFramer().feed(message)

        assertEquals(1, messages.size)
        val parsed = assertInstanceOf(SASLInitialResponse::class.java, messages[0])
        assertEquals("clientNonceABC123", parsed.getClientNonce())
    }
}
