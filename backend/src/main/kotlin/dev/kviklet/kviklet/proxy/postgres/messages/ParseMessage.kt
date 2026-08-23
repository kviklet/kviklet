// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres.messages

import java.nio.ByteBuffer

class ParseMessage(
    override val header: Char = 'P',
    override val length: Int,
    override val originalContent: ByteArray,
    val query: String,
    val statementName: String,
    val parameterTypes: List<Int>,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): ParseMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val statementName = readCString(buffer)
            val queryString = readCString(buffer)
            // The count is an unsigned int16 on the wire
            val parameterCount = buffer.short.toInt() and 0xFFFF
            val parameterTypes = mutableListOf<Int>()
            for (i in 0 until parameterCount) {
                parameterTypes.add(buffer.int)
            }
            return ParseMessage('P', length, bytes, queryString, statementName, parameterTypes)
        }
    }
}
