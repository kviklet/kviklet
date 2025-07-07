package dev.kviklet.kviklet.proxy.postgres.messages

import java.nio.ByteBuffer
import java.nio.charset.Charset

class ParseMessage(
    override val header: Char = 'P',
    override val length: Int,
    override val originalContent: ByteArray,
    val query: String,
    val statementName: String,
    val parameterTypes: List<Int>,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        // Strings are denoted with a zero byte at the end
        fun fromBytes(length: Int, bytes: ByteArray): ParseMessage {
            val buffer = ByteBuffer.wrap(bytes)
            // read the query string until the first zero byte
            val statementNameBytes = mutableListOf<Byte>()
            while (true) {
                val byte = buffer.get()
                if (byte == 0.toByte()) {
                    break
                }
                statementNameBytes.add(byte)
            }
            val statementName = String(statementNameBytes.toByteArray(), Charset.forName("UTF-8"))
            // read the statement name until the first zero byte // TODO: ?
            val query = mutableListOf<Byte>()
            while (true) {
                val byte = buffer.get()
                if (byte == 0.toByte()) {
                    break
                }
                query.add(byte)
            }
            val queryString = String(query.toByteArray(), Charset.forName("UTF-8"))
            // read the parameter types
            val parameterCount = buffer.short.toInt()
            val parameterTypes = mutableListOf<Int>()
            for (i in 0 until parameterCount) {
                parameterTypes.add(buffer.int)
            }
            return ParseMessage('P', length, bytes, queryString, statementName, parameterTypes)
        }
    }
}
