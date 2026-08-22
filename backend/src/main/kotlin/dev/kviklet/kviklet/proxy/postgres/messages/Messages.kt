// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres.messages

import java.nio.ByteBuffer
import java.nio.charset.Charset

class PGTypeStringifier(
    private val pgTypeMap: Map<Int, String> = mapOf(
        16 to "bool",
        17 to "bytea",
        18 to "char",
        19 to "name",
        20 to "int8",
        21 to "int2",
        22 to "int2vector",
        23 to "int4",
        24 to "regproc",
        25 to "text",
        26 to "oid",
        27 to "tid",
        28 to "xid",
        29 to "cid",
        30 to "oidvector",
        71 to "pg_type",
        75 to "pg_attribute",
        81 to "pg_proc",
        83 to "pg_class",
        114 to "json",
        142 to "xml",
        143 to "_xml",
        199 to "_json",
        194 to "pg_node_tree",
        210 to "smgr",
        600 to "point",
        601 to "lseg",
        602 to "path",
        603 to "box",
        604 to "polygon",
        628 to "line",
        629 to "_line",
        650 to "cidr",
        651 to "_cidr",
        700 to "float4",
        701 to "float8",
        702 to "abstime",
        703 to "reltime",
        704 to "tinterval",
        705 to "unknown",
        718 to "circle",
        719 to "_circle",
        790 to "money",
        791 to "_money",
        829 to "macaddr",
        869 to "inet",
        1000 to "_bool",
        1001 to "_bytea",
        1002 to "_char",
        1003 to "_name",
        1005 to "_int2",
        1006 to "_int2vector",
        1007 to "_int4",
        1008 to "_regproc",
        1009 to "_text",
        1010 to "_tid",
        1011 to "_xid",
        1012 to "_cid",
        1013 to "_oidvector",
        1014 to "_bpchar",
        1015 to "_varchar",
        1016 to "_int8",
        1042 to "bpchar",
        1043 to "varchar",
    ),
) {
    fun convertToHumanReadableString(typeObjectId: Int, bytes: ByteArray): String {
        val type = pgTypeMap[typeObjectId]
        return when (type) {
            "bool" -> {
                (bytes[0].toInt() != 0).toString()
            }

            "char" -> {
                bytes[0].toInt().toChar().toString()
            }

            "name" -> {
                bytes.toHexString()
            }

            "int8" -> {
                ByteBuffer.wrap(bytes).long.toString()
            }

            "int2" -> {
                ByteBuffer.wrap(bytes).short.toString()
            }

            "int4" -> {
                ByteBuffer.wrap(bytes).int.toString()
            }

            "float4" -> {
                ByteBuffer.wrap(bytes).float.toString()
            }

            "float8" -> {
                ByteBuffer.wrap(bytes).double.toString()
            }

            "text", "varchar", "bpchar" -> {
                String(bytes, Charset.forName("UTF-8"))
            }

            "oid" -> {
                ByteBuffer.wrap(bytes).int.toString()
            }

            else -> {
                bytes.toHexString()
            }
        }
    }
}

open class ParsedMessage(open val header: Char, open val length: Int, open val originalContent: ByteArray) {
    override fun toString(): String = "ParsedMessage(header=$header, length=$length)"

    fun isTermination(): Boolean = header == 'X'

    open fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(5)
        buffer.put(header.code.toByte())
        buffer.putInt(length)
        return buffer.array() + originalContent
    }

    companion object {
        fun fromBytes(buffer: ByteBuffer): ParsedMessage {
            // todo if check if length is < 4, funny things happen otherwise. The error is handled in the other fromBytes method
            // but if the length is e.g. 1, the code will try to allocate array with negative size.
            val header = buffer.get().toInt().toChar()
            val length = buffer.int
            val messageBytes = ByteArray(length - 4)
            buffer.get(messageBytes)
            return fromBytes(header, length, messageBytes)
        }

        fun fromBytes(header: Char, length: Int, bytes: ByteArray): ParsedMessage {
            if (bytes.size < length - 4) {
                throw Exception("Not enough bytes to parse message")
            }
            return when (header) {
                'X' -> TerminationMessage.fromBytes(length, bytes)
                'p' -> SASLInitialResponse.fromBytes(length, bytes)
                'Q' -> QueryMessage.fromBytes(length, bytes)
                'P' -> ParseMessage.fromBytes(length, bytes)
                'E' -> ExecuteMessage.fromBytes(length, bytes)
                'B' -> BindMessage.fromBytes(length, bytes)
                'C' -> CloseMessage.fromBytes(length, bytes)
                'S' -> SyncMessage.fromBytes(length)
                else -> ParsedMessage(header, length, bytes)
            }
        }
    }
}

class TerminationMessage(header: Char = 'X', length: Int = 4, originalContent: ByteArray) :
    ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): TerminationMessage = TerminationMessage('X', length, bytes)
    }
}

class MessageOrBytes(val message: ParsedMessage?, val bytes: ByteArray?, val response: ByteArray? = null)

// todo: move MessageOrBytes.writableBytes() and MessageOrBytes.isTermination() to the class
fun MessageOrBytes.writableBytes(): ByteArray = this.message?.toByteArray() ?: this.bytes!!

fun MessageOrBytes.isTermination(): Boolean = this.message?.isTermination() ?: false

class QueryMessage(
    override val header: Char = 'Q',
    override val length: Int,
    originalContent: ByteArray,
    val query: String,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): QueryMessage {
            val query = String(bytes.copyOfRange(0, bytes.size - 1), Charset.forName("UTF-8"))
            return QueryMessage('Q', length, originalContent = bytes, query = query)
        }
    }
}

// Reads a zero-terminated string from the buffer, as used throughout the Postgres wire protocol
fun readCString(buffer: ByteBuffer): String {
    val stringBytes = mutableListOf<Byte>()
    while (true) {
        val byte = buffer.get()
        if (byte == 0.toByte()) {
            break
        }
        stringBytes.add(byte)
    }
    return String(stringBytes.toByteArray(), Charset.forName("UTF-8"))
}

class ExecuteMessage(
    override val header: Char = 'E',
    override val length: Int,
    originalContent: ByteArray,
    val portalName: String,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): ExecuteMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val portalName = readCString(buffer)
            return ExecuteMessage('E', length, bytes, portalName)
        }
    }
}

class BindMessage(
    override val header: Char = 'B',
    override val length: Int,
    override val originalContent: ByteArray,
    val portalName: String,
    val statementName: String,
    val parameterFormatCodes: List<Int>,
    val parameters: List<ByteArray?>,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): BindMessage {
            val buffer = ByteBuffer.wrap(bytes)
            // The Bind message carries the destination portal name first and the source statement name second
            val portalName = readCString(buffer)
            val statementName = readCString(buffer)
            val parameterFormatCount = buffer.short.toInt()
            val parameterFormatCodes = mutableListOf<Int>()
            for (i in 0 until parameterFormatCount) {
                parameterFormatCodes.add(buffer.short.toInt())
            }
            val parameterCount = buffer.short.toInt()
            val parameterValues = mutableListOf<ByteArray?>()
            for (i in 0 until parameterCount) {
                val parameterLength = buffer.int
                if (parameterLength == -1) {
                    // -1 is the wire encoding of NULL, there are no value bytes to read
                    parameterValues.add(null)
                } else {
                    val parameterBytes = ByteArray(parameterLength)
                    buffer.get(parameterBytes)
                    parameterValues.add(parameterBytes)
                }
            }
            val resultFormatCount = buffer.short.toInt()
            val resultFormatCodes = mutableListOf<Int>()
            for (i in 0 until resultFormatCount) {
                resultFormatCodes.add(buffer.short.toInt())
            }
            return BindMessage(
                'B',
                length,
                bytes,
                portalName,
                statementName,
                parameterFormatCodes,
                parameterValues,
            )
        }
    }
}

class CloseMessage(
    override val header: Char = 'C',
    override val length: Int,
    originalContent: ByteArray,
    val closeType: Char,
    val name: String,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): CloseMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val closeType = buffer.get().toInt().toChar()
            val name = readCString(buffer)
            return CloseMessage('C', length, bytes, closeType, name)
        }
    }
}

class SyncMessage(override val header: Char = 'S', override val length: Int) :
    ParsedMessage(header, length, ByteArray(0)) {
    companion object {
        fun fromBytes(length: Int): SyncMessage = SyncMessage('S', length)
    }
}

class Statement(
    val query: String,
    private val parameterFormatCodes: List<Int> = listOf(),
    val parameterTypes: List<Int> = listOf(),
    private val boundParams: List<ByteArray?> = listOf(),
) {
    override fun toString(): String = "Statement(query='$query', parameterFormatCodes=$parameterFormatCodes)," +
        "interpolated query: ${interpolateQuery()}"

    // Replaces $1, $2, ... with the bound parameter values for the audit log. The replacement is done
    // in a single pass so parameter values are never re-scanned for placeholders, and rendering a
    // parameter must never throw: a value that cannot be decoded is rendered as hex instead.
    fun interpolateQuery(): String = Regex("\\$(\\d+)").replace(query) { match ->
        val index = match.groupValues[1].toInt() - 1
        if (index in boundParams.indices) renderParameter(index) else match.value
    }

    private fun renderParameter(index: Int): String {
        val param = boundParams[index] ?: return "NULL"
        val text = try {
            if (isTextFormat(index)) {
                String(param, Charset.forName("UTF-8"))
            } else {
                PGTypeStringifier().convertToHumanReadableString(parameterTypes.getOrElse(index) { 0 }, param)
            }
        } catch (e: Exception) {
            param.toHexString()
        }
        return "'${text.replace("'", "''")}'"
    }

    // Per the protocol, no format codes means all-text, a single code applies to all parameters,
    // otherwise there is one code per parameter. 0 is text, 1 is binary.
    private fun isTextFormat(index: Int): Boolean = when (parameterFormatCodes.size) {
        0 -> true
        1 -> parameterFormatCodes[0] == 0
        else -> parameterFormatCodes.getOrElse(index) { 0 } == 0
    }
}

// Messages returned by the proxy. All of those are used during connection setup
fun readyForQuery(): ByteArray {
    val responseBuffer = ByteBuffer.allocate(6)
    responseBuffer.put('Z'.code.toByte())
    responseBuffer.putInt(5)
    responseBuffer.put('I'.code.toByte())
    return responseBuffer.array()
}

fun paramMessage(key: String, value: String): ByteArray {
    val responseBuffer = ByteBuffer.allocate(
        7 + key.toByteArray().size + value.toByteArray().size,
    )
    responseBuffer.put('S'.code.toByte())
    responseBuffer.putInt(6 + key.toByteArray().size + value.toByteArray().size)
    responseBuffer.put(key.toByteArray())
    responseBuffer.put(0.toByte())
    responseBuffer.put(value.toByteArray())
    responseBuffer.put(0.toByte())
    return responseBuffer.array()
}

fun errorResponse(message: String): ByteArray {
    val fields = listOf(
        'S' to "ERROR",
        'V' to "ERROR",
        'C' to "08P01", // protocol_violation
        'M' to message,
    )
    val fieldBytes = fields.flatMap { (type, value) ->
        listOf(type.code.toByte()) + value.toByteArray().toList() + listOf(0.toByte())
    } + listOf(0.toByte())
    val responseBuffer = ByteBuffer.allocate(5 + fieldBytes.size)
    responseBuffer.put('E'.code.toByte())
    responseBuffer.putInt(4 + fieldBytes.size)
    responseBuffer.put(fieldBytes.toByteArray())
    return responseBuffer.array()
}

fun backendKeyData(): ByteArray {
    val responseBuffer = ByteBuffer.allocate(13)
    responseBuffer.put('K'.code.toByte())
    responseBuffer.putInt(12)
    responseBuffer.putInt(0)
    responseBuffer.putInt(0)
    return responseBuffer.array()
}
fun isStartupMessage(byteArray: ByteArray): Boolean = byteArray[4] == 0x00.toByte() &&
    byteArray[5] == 0x03.toByte() &&
    byteArray[6] == 0x00.toByte() &&
    byteArray[7] == 0x00.toByte()
fun startupMessageContainsValidUser(message: ByteArray, msgLen: Int, username: String): Boolean =
    !String(message).subSequence(8, msgLen).contains(username)
fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
