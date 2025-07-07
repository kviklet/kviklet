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
        650 to "_bool",
        869 to "_bytea",
        1000 to "_char",
        1001 to "_name",
        1002 to "_int2",
        1003 to "_int2vector",
        1005 to "_int4",
        1006 to "_regproc",
        1007 to "_text",
        1008 to "_tid",
        1009 to "_xid",
        1010 to "_cid",
        1011 to "_oidvector",
        1012 to "_bpchar",
        1013 to "_varchar",
        1014 to "_int8",
    ),
) {
    fun convertToHumanReadableString(typeObjectId: Int, bytes: ByteArray): String {
        val type = pgTypeMap[typeObjectId]
        return when (type) {
            "bool" -> {
                (bytes[0] == 0x01.toByte()).toString()
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

            "text" -> {
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

class ExecuteMessage(
    override val header: Char = 'E',
    override val length: Int,
    originalContent: ByteArray,
    val statementName: String,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): ExecuteMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val statementNameBytes = mutableListOf<Byte>()
            while (true) {
                val byte = buffer.get()
                if (byte == 0.toByte()) {
                    break
                }
                statementNameBytes.add(byte)
            }
            val statementName = String(statementNameBytes.toByteArray(), Charset.forName("UTF-8"))
            return ExecuteMessage('E', length, bytes, statementName)
        }
    }
}

class BindMessage(
    override val header: Char = 'B',
    override val length: Int,
    override val originalContent: ByteArray,
    val statementName: String,
    val parameterFormatCodes: List<Int>,
    val parameters: List<ByteArray>,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): BindMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val statementNameBytes = mutableListOf<Byte>()
            while (true) {
                val byte = buffer.get()
                if (byte == 0.toByte()) {
                    break
                }
                statementNameBytes.add(byte)
            }
            val statementName = String(statementNameBytes.toByteArray(), Charset.forName("UTF-8"))
            val portalNameBytes = mutableListOf<Byte>()
            while (true) {
                val byte = buffer.get()
                if (byte == 0.toByte()) {
                    break
                }
                portalNameBytes.add(byte)
            }
            val parameterFormatCount = buffer.short.toInt()
            val parameterFormatCodes = mutableListOf<Int>()
            for (i in 0 until parameterFormatCount) {
                parameterFormatCodes.add(buffer.short.toInt())
            }
            val parameterCount = buffer.short.toInt()
            val parameterValues = mutableListOf<ByteArray>()
            for (i in 0 until parameterCount) {
                val parameterLength = buffer.int
                val parameterBytes = ByteArray(parameterLength)
                buffer.get(parameterBytes)
                parameterValues.add(parameterBytes)
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
                statementName,
                parameterFormatCodes,
                parameterValues,
            )
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
    private val parameterFormatCodes: List<Int> = mutableListOf(),
    val parameterTypes: List<Int> = mutableListOf(),
    private val boundParams: List<ByteArray> = mutableListOf(),
) {
    override fun toString(): String =
        "Statement(query='$query', parameterFormatCodes=$parameterFormatCodes, boundParams=$boundParams)," +
            "interpolated query: ${interpolateQuery()}"

    fun interpolateQuery(): String {
        var interpolatedQuery = query
        for (i in boundParams.indices) {
            val param = boundParams[i]
            val paramType = parameterTypes[i]
            val paramIndex = i + 1
            interpolatedQuery = interpolatedQuery.replace(
                "$$paramIndex",
                "'${PGTypeStringifier().convertToHumanReadableString(paramType, param)}'",
            )
        }
        return interpolatedQuery
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
