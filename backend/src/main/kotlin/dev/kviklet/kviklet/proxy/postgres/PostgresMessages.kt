package dev.kviklet.kviklet.proxy.postgres

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest

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
        fun fromBytes(buffer: ByteBuffer) : ParsedMessage {
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
                'p' -> {
                    HashedPasswordMessage.fromBytes(length, bytes)
                }

                'Q' -> {
                    QueryMessage.fromBytes(length, bytes)
                }

                'P' -> {
                    ParseMessage.fromBytes(length, bytes)
                }

                'E' -> {
                    ExecuteMessage.fromBytes(length, bytes)
                }

                'B' -> {
                    BindMessage.fromBytes(length, bytes)
                }

                'S' -> {
                    SyncMessage.fromBytes(length)
                }

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
fun MessageOrBytes.writableBytes() : ByteArray { return this.message?.toByteArray() ?: this.bytes!!}
fun MessageOrBytes.isTermination() : Boolean { return this.message?.isTermination() ?: false }

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
            // read the statement name until the first zero byte
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

class HashedPasswordMessage(
    override val header: Char = 'p',
    override val length: Int,
    override val originalContent: ByteArray,
    val message: String,
    val zeroByte: Byte = 0,
) : ParsedMessage(header, length, originalContent) {

    override fun toByteArray(): ByteArray {
        val messageBytes = message.toByteArray()
        val bufferSize = 1 + 4 + messageBytes.size + 1 // size for header, length, message, and zeroByte
        val buffer = ByteBuffer.allocate(bufferSize)

        buffer.put(header.code.toByte())
        buffer.putInt(length)
        buffer.put(messageBytes)
        buffer.put(zeroByte)

        return buffer.array()
    }

    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): HashedPasswordMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val messageLength = length - 4 // Subtract length of 'length' itself and 'zeroByte'
            val messageBytes = ByteArray(messageLength - 1)
            buffer.get(messageBytes)
            val message = String(messageBytes)
            val zeroByte = buffer.get()

            return HashedPasswordMessage('p', length, bytes, message, zeroByte)
        }

        fun from(username: String, password: String, salt: ByteArray): HashedPasswordMessage {
            val message = passwordContent(username, password, salt)
            val length = message.size + 5
            return HashedPasswordMessage('p', length, ByteArray(0), message.toString(Charset.forName("UTF-8")), 0)
        }

        fun passwordContent(username: String, password: String, salt: ByteArray): ByteArray {
            // Implemented after https://www.postgresql.org/docs/current/protocol-flow.html#PROTOCOL-FLOW-START-UP
            // The documentation says nothing about the last zero byte but datagrip sends it and it works, so we do too
            val digest = MessageDigest.getInstance("MD5")
            val md5 = digest.digest(password.toByteArray() + username.toByteArray())
            val md5asString = md5.toHexString()
            val md5WithSalt = digest.digest(md5asString.toByteArray() + salt)
            val message = "md5${md5WithSalt.toHexString()}".toByteArray()
            return message
        }
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

fun readyForQuery(): ByteArray {
    val responseBuffer = ByteBuffer.allocate(6)
    responseBuffer.put('Z'.code.toByte())
    responseBuffer.putInt(5)
    responseBuffer.put('I'.code.toByte())
    return responseBuffer.array()
}

fun authenticationOk(): ByteArray {
    val responseBuffer = ByteBuffer.allocate(9)
    responseBuffer.put('R'.code.toByte())
    responseBuffer.putInt(8)
    responseBuffer.putInt(0)
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

fun isSSLRequest(byteArray: ByteArray): Boolean {
    return byteArray[0] == 0x00.toByte() &&
            byteArray[1] == 0x00.toByte() &&
            byteArray[2] == 0x00.toByte() &&
            byteArray[3] == 0x08.toByte() &&
            byteArray[4] == 0x04.toByte() &&
            byteArray[5] == 0xd2.toByte() &&
            byteArray[6] == 0x16.toByte() &&
            byteArray[7] == 0x2f.toByte()
}

fun isStartupMessage(byteArray: ByteArray): Boolean {
    return byteArray[4] == 0x00.toByte() &&
            byteArray[5] == 0x03.toByte() &&
            byteArray[6] == 0x00.toByte() &&
            byteArray[7] == 0x00.toByte()
}

fun createAuthenticationMD5PasswordMessage(salt: Int): ByteArray {
    val responseBuffer = ByteBuffer.allocate(13)
    responseBuffer.put('R'.code.toByte())
    responseBuffer.putInt(12)
    responseBuffer.putInt(5)
    responseBuffer.putInt(salt)
    return responseBuffer.array()
}

fun confirmPasswordMessage(message: HashedPasswordMessage, username: String, expectedPassword: String, md5Salt: ByteArray) {
    val password = message.message
    val expectedMessage = HashedPasswordMessage.passwordContent(username, expectedPassword, md5Salt)
    if (!password.toByteArray().contentEquals(expectedMessage)) {
        throw Exception("Password does not match")
    }
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }

fun tlsNotSupportedMessage() : ByteArray{
    return "N".toByteArray()
}

fun tlsSupportedMessage() : ByteArray {
    return "S".toByteArray()
}