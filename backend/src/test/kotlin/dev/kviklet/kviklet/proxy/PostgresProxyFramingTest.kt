// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.messages.MessageFramer
import dev.kviklet.kviklet.proxy.postgres.messages.QueryMessage
import dev.kviklet.kviklet.proxy.postgres.readFromAnyStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

class PostgresProxyFramingTest {

    // Builds a full wire-protocol Query message: 'Q', int32 length, query text, zero terminator
    private fun queryMessageBytes(query: String): ByteArray {
        val queryBytes = query.toByteArray() + byteArrayOf(0)
        val buffer = ByteBuffer.allocate(5 + queryBytes.size)
        buffer.put('Q'.code.toByte())
        buffer.putInt(4 + queryBytes.size)
        buffer.put(queryBytes)
        return buffer.array()
    }

    @Test
    fun `a complete message in a single chunk is parsed`() {
        val framer = MessageFramer()
        val messages = framer.feed(queryMessageBytes("SELECT 1"))
        assertEquals(1, messages.size)
        assertEquals("SELECT 1", (messages[0] as QueryMessage).query)
    }

    @Test
    fun `multiple messages in a single chunk are all parsed`() {
        val framer = MessageFramer()
        val messages = framer.feed(queryMessageBytes("SELECT 1") + queryMessageBytes("SELECT 2"))
        assertEquals(2, messages.size)
        assertEquals("SELECT 1", (messages[0] as QueryMessage).query)
        assertEquals("SELECT 2", (messages[1] as QueryMessage).query)
    }

    @Test
    fun `a message split in the middle of its header is reassembled`() {
        val framer = MessageFramer()
        val bytes = queryMessageBytes("SELECT 1")
        assertEquals(0, framer.feed(bytes.copyOfRange(0, 3)).size)
        val messages = framer.feed(bytes.copyOfRange(3, bytes.size))
        assertEquals(1, messages.size)
        assertEquals("SELECT 1", (messages[0] as QueryMessage).query)
    }

    @Test
    fun `a message split in the middle of its body is reassembled`() {
        val framer = MessageFramer()
        val bytes = queryMessageBytes("SELECT 'split right here'")
        assertEquals(0, framer.feed(bytes.copyOfRange(0, 15)).size)
        val messages = framer.feed(bytes.copyOfRange(15, bytes.size))
        assertEquals(1, messages.size)
        assertEquals("SELECT 'split right here'", (messages[0] as QueryMessage).query)
    }

    @Test
    fun `a message larger than the socket read buffer is reassembled from many chunks`() {
        val framer = MessageFramer()
        val query = "SELECT '" + "x".repeat(100_000) + "'"
        val bytes = queryMessageBytes(query)
        val parsed = mutableListOf<QueryMessage>()
        for (offset in bytes.indices step 8192) {
            val chunk = bytes.copyOfRange(offset, minOf(offset + 8192, bytes.size))
            framer.feed(chunk).forEach { parsed.add(it as QueryMessage) }
        }
        assertEquals(1, parsed.size)
        assertEquals(query, parsed[0].query)
    }

    @Test
    fun `a trailing partial message is buffered while complete messages are returned`() {
        val framer = MessageFramer()
        val second = queryMessageBytes("SELECT 2")
        val firstChunk = queryMessageBytes("SELECT 1") + second.copyOfRange(0, 7)
        val firstBatch = framer.feed(firstChunk)
        assertEquals(1, firstBatch.size)
        assertEquals("SELECT 1", (firstBatch[0] as QueryMessage).query)
        val secondBatch = framer.feed(second.copyOfRange(7, second.size))
        assertEquals(1, secondBatch.size)
        assertEquals("SELECT 2", (secondBatch[0] as QueryMessage).query)
    }

    @Test
    fun `a message length below the protocol minimum fails the parse`() {
        val framer = MessageFramer()
        val buffer = ByteBuffer.allocate(5)
        buffer.put('Q'.code.toByte())
        buffer.putInt(1) // the length includes itself, anything below 4 is invalid
        assertThrows<Exception> { framer.feed(buffer.array()) }
    }

    @Test
    fun `a message length above the protocol maximum fails the parse instead of buffering forever`() {
        val framer = MessageFramer()
        val buffer = ByteBuffer.allocate(5)
        buffer.put('Q'.code.toByte())
        buffer.putInt(Int.MAX_VALUE)
        assertThrows<Exception> { framer.feed(buffer.array()) }
    }

    @Test
    fun `reading from a stream at EOF reports the stream as closed`() {
        val received = mutableListOf<ByteArray>()
        val open = readFromAnyStream(ByteArrayInputStream(ByteArray(0))) { received.add(it) }
        assertFalse(open)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `reading a stream that ends after a single byte still delivers that byte`() {
        val received = mutableListOf<ByteArray>()
        val open = readFromAnyStream(ByteArrayInputStream(byteArrayOf(42))) { received.add(it) }
        assertFalse(open)
        assertEquals(1, received.size)
        assertEquals(42, received[0][0].toInt())
    }

    @Test
    fun `reading a stream with data delivers all of it and keeps the stream open`() {
        val data = ByteArray(100) { it.toByte() }
        val received = mutableListOf<ByteArray>()
        val open = readFromAnyStream(ByteArrayInputStream(data)) { received.add(it) }
        assertTrue(open)
        assertEquals(1, received.size)
        assertTrue(data.contentEquals(received[0]))
    }
}
