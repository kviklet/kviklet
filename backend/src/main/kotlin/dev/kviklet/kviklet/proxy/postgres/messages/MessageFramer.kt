// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres.messages

import java.nio.ByteBuffer

// Postgres rejects wire-protocol messages larger than 1GB, so a length beyond that can only be
// garbage or an attack and waiting for the remaining bytes would buffer unbounded amounts of data
private const val MAX_MESSAGE_LENGTH = 0x40000000

// Reassembles wire-protocol messages from the raw chunks read off the client socket. A single
// read can contain several messages and a single message can be split across several reads
// (anything larger than the socket read buffer always is), so complete messages are extracted
// and the trailing partial message is buffered until the next chunk arrives.
class MessageFramer {
    private var buffered: ByteArray = ByteArray(0)

    fun feed(chunk: ByteArray): List<ParsedMessage> {
        buffered += chunk
        val messages = mutableListOf<ParsedMessage>()
        var offset = 0
        // A message is one header byte plus an int32 length that includes itself but not the header
        while (buffered.size - offset >= 5) {
            val length = ByteBuffer.wrap(buffered, offset + 1, 4).int
            if (length < 4 || length > MAX_MESSAGE_LENGTH) {
                throw Exception(
                    "Invalid length $length for message of type '${buffered[offset].toInt().toChar()}'",
                )
            }
            if (buffered.size - offset < 1 + length) {
                break
            }
            val header = buffered[offset].toInt().toChar()
            val content = buffered.copyOfRange(offset + 5, offset + 1 + length)
            messages.add(ParsedMessage.fromBytes(header, length, content))
            offset += 1 + length
        }
        buffered = buffered.copyOfRange(offset, buffered.size)
        return messages
    }
}
