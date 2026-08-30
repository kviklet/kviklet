// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres.messages

import java.nio.ByteBuffer

// Postgres rejects wire-protocol messages larger than 1GB, so a length beyond that can only be
// garbage or an attack and waiting for the remaining bytes would buffer unbounded amounts of data
private const val MAX_MESSAGE_LENGTH = 0x40000000

// Above this capacity the buffer is released once fully drained instead of kept for reuse.
private const val SHRINK_THRESHOLD = 0x10000

// Reassembles wire-protocol messages from the raw chunks read off the client socket. A single
// read can contain several messages and a single message can be split across several reads
// (anything larger than the socket read buffer always is), so complete messages are extracted
// and the trailing partial message is buffered until the next chunk arrives.
//
// The buffer is an explicit [start, end) window over a growable array, not a re-allocated
// ByteArray: appending with `buffered += chunk` would copy the whole accumulated partial message
// on every socket read, which is quadratic in message size -- ruinous for anything large (a big
// Bind parameter, COPY FROM STDIN) since a message may legally run to 1GB. Unconsumed bytes only
// move when the array has to grow (doubling) or when the free tail is exhausted, so every byte is
// copied O(1) times amortized, and in the common case -- the buffer drained by the previous feed --
// bytes are copied in exactly once.
class MessageFramer {
    private var buffered = ByteArray(0)

    // The unconsumed window: buffered[start, end) holds the trailing partial message, if any.
    private var start = 0
    private var end = 0

    fun feed(chunk: ByteArray): List<ParsedMessage> {
        append(chunk)
        val messages = mutableListOf<ParsedMessage>()
        // A message is one header byte plus an int32 length that includes itself but not the header
        while (end - start >= 5) {
            val length = ByteBuffer.wrap(buffered, start + 1, 4).int
            if (length < 4 || length > MAX_MESSAGE_LENGTH) {
                throw Exception(
                    "Invalid length $length for message of type '${buffered[start].toInt().toChar()}'",
                )
            }
            if (end - start < 1 + length) {
                break
            }
            val header = buffered[start].toInt().toChar()
            val content = buffered.copyOfRange(start + 5, start + 1 + length)
            messages.add(ParsedMessage.fromBytes(header, length, content))
            start += 1 + length
        }
        if (start == end) {
            // Fully drained: reset the window so the next append writes at the front and never
            // needs to slide or grow while traffic consists of complete, ordinary-sized messages.
            start = 0
            end = 0
            if (buffered.size > SHRINK_THRESHOLD) {
                // Don't let one huge message (up to 1GB) stay pinned as this connection's buffer
                // for the rest of the session.
                buffered = ByteArray(0)
            }
        }
        return messages
    }

    private fun append(chunk: ByteArray) {
        val unconsumed = end - start
        if (end + chunk.size > buffered.size) {
            if (unconsumed + chunk.size <= buffered.size) {
                // Enough capacity overall, just none left behind `end`: slide the unconsumed
                // bytes to the front instead of growing.
                System.arraycopy(buffered, start, buffered, 0, unconsumed)
            } else {
                // Doubling (not grow-to-fit) is what makes the copying amortized linear while a
                // large message accumulates.
                val grown = ByteArray(maxOf(buffered.size * 2, unconsumed + chunk.size))
                System.arraycopy(buffered, start, grown, 0, unconsumed)
                buffered = grown
            }
            start = 0
            end = unconsumed
        }
        System.arraycopy(chunk, 0, buffered, end, chunk.size)
        end += chunk.size
    }
}
