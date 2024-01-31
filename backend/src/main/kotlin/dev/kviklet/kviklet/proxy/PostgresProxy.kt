package dev.kviklet.kviklet.proxy

import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest

class Statement(
    val query: String,
    val parameterFormatCodes: List<Int> = mutableListOf(),
    val parameterTypes: List<Int> = mutableListOf(),
    val boundParams: List<ByteArray> = mutableListOf(),
) {
    override fun toString(): String {
        return "Statement(query='$query', parameterFormatCodes=$parameterFormatCodes, boundParams=$boundParams)," +
            "interpolated query: ${interpolateQuery()}"
    }

    fun interpolateQuery(): String {
        var interpolatedQuery = query
        for (i in 0 until boundParams.size) {
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

class PostgresProxy(
    private val targetHost: String,
    private val targetPort: Int,
    private val pgTypeStringifier: PGTypeStringifier = PGTypeStringifier(),
) {
    private var md5Salt = byteArrayOf()

    private val boundStatements: MutableMap<String, Statement> = mutableMapOf()

    fun startServer(port: Int) {
        ServerSocket(port).use { serverSocket ->
            println("Server started on port $port")

            while (true) {
                val clientSocket = serverSocket.accept()
                println("Accepted connection from ${clientSocket.inetAddress}:${clientSocket.port}")
                handleClient(clientSocket)
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        clientSocket.use { socket ->
            val targetSocket = Socket(targetHost, targetPort)
            targetSocket.use { forwardSocket ->
                val clientInput = socket.getInputStream()
                val clientOutput = socket.getOutputStream()
                val targetInput = forwardSocket.getInputStream()
                val targetOutput = forwardSocket.getOutputStream()

                // Continuously relay data in both directions
                relayContinuously(clientInput, targetOutput, targetInput, clientOutput)
            }
        }
    }

    private fun relayContinuously(
        clientInput: InputStream,
        targetOutput: OutputStream,
        targetInput: InputStream,
        clientOutput: OutputStream,
    ) {
        val clientBuffer = ByteArray(8192)
        val targetBuffer = ByteArray(8192)
        var lastClientMessage: MessageOrBytes? = null

        while (true) {
            if (clientInput.available() > 0) {
                val bytesRead = clientInput.read(clientBuffer)
                val data = clientBuffer.copyOf(bytesRead)
                val hexData = data.joinToString(separator = " ") { byte -> "%02x".format(byte) }
                val stringData = String(clientBuffer, 0, bytesRead, Charset.forName("UTF-8"))
                println(
                    "Client to Target: $hexData [${stringData.filter {
                        it.isLetterOrDigit() || it.isWhitespace()
                    }}]",
                )
                for (clientMessage in parseDataToMessages(data)) {
                    if (clientMessage.message is ParseMessage) {
                        clientMessage.message.printQuery()
                    }
                    val newData = clientMessage.message?.toByteArray() ?: clientMessage.bytes!!
                    println(
                        "Adapted data to Target: ${newData.joinToString(separator = " ") { byte ->
                            "%02x".format(byte)
                        }} [${String(
                            newData,
                            Charset.forName("UTF-8"),
                        ).filter { it.isLetterOrDigit() || it.isWhitespace() }}]",
                    )
                    targetOutput.write(newData, 0, newData.size)
                    targetOutput.flush()
                    lastClientMessage = clientMessage
                }
            }

            if (targetInput.available() > 0) {
                val bytesRead = targetInput.read(targetBuffer)
                val data = targetBuffer.copyOf(bytesRead)
                parseResponse(data)
                val hexData = data.joinToString(separator = " ") { byte -> "%02x".format(byte) }
                val stringData = String(targetBuffer, 0, bytesRead, Charset.forName("UTF-8"))
                println(
                    "Target to Client: $hexData [${stringData.filter { it.isLetterOrDigit() || it.isWhitespace() }}]",
                )
                clientOutput.write(targetBuffer, 0, bytesRead)
                clientOutput.flush()
            }

            if (lastClientMessage?.message?.isTermination() == true) {
                println("Terminating")
                break
            }

            // Sleep briefly to prevent a tight loop that consumes too much CPU
            // You can adjust the sleep duration to balance responsiveness and CPU usage
            Thread.sleep(10)
        }
    }

    private fun parseDataToMessages(byteArray: ByteArray): List<MessageOrBytes> {
        // Check for SSL Request and startup message
        val buffer = ByteBuffer.wrap(byteArray)
        if (
            byteArray[0] == 0x00.toByte() &&
            byteArray[1] == 0x00.toByte() &&
            byteArray[2] == 0x00.toByte() &&
            byteArray[3] == 0x08.toByte() &&
            byteArray[4] == 0x04.toByte() &&
            byteArray[5] == 0xd2.toByte() &&
            byteArray[6] == 0x16.toByte() &&
            byteArray[7] == 0x2f.toByte()
        ) {
            println("SSL Request")
            return listOf(MessageOrBytes(null, byteArray))
        } else if (
            byteArray[0] == 0x00.toByte()
        ) {
            println("Startup")
            val length = buffer.getInt() // Length
            val protocolVersion = buffer.getInt() // Protocol version
            val parameters = mutableListOf<Pair<String, String>>()
            println(length)
            println(protocolVersion)
            println(parameters)
            return listOf(MessageOrBytes(null, byteArray))
        }
        val messages = mutableListOf<MessageOrBytes>()
        while (buffer.remaining() > 0) {
            val header = buffer.get().toInt().toChar()
            val length = buffer.int
            val messageBytes = ByteArray(length - 4)
            buffer.get(messageBytes)
            val parsedMessage = ParsedMessage.fromBytes(header, length, messageBytes)

            println(header)
            println(length)
            println(parsedMessage)
            if (parsedMessage is HashedPasswordMessage) {
                messages.add(replacePasswordMessage(parsedMessage))
                continue
            }
            if (parsedMessage is ParseMessage) {
                println("Parse message with ${parsedMessage.query}")
                boundStatements[parsedMessage.statementName] = Statement(
                    parsedMessage.query,
                    parameterTypes = parsedMessage.parameterTypes,
                )
            }
            if (parsedMessage is BindMessage) {
                println("Bind message with ${parsedMessage.statementName}")
                val statement = boundStatements[parsedMessage.statementName]!!
                boundStatements[parsedMessage.statementName] = Statement(
                    statement.query,
                    parsedMessage.parameterFormatCodes,
                    statement.parameterTypes,
                    parsedMessage.parameters,
                )
            }
            if (parsedMessage is ExecuteMessage) {
                println("Execute message with ${parsedMessage.statementName}")
                val statement = boundStatements[parsedMessage.statementName]!!
                println(statement)
            }

            messages.add(MessageOrBytes(parsedMessage, null))
        }
        return messages
    }

    private fun replacePasswordMessage(message: HashedPasswordMessage): MessageOrBytes {
        val password = message.message
        val expectedMessage = HashedPasswordMessage.passwordContent("postgres", "bla", md5Salt)
        println("Expected message: ${expectedMessage.toHexString()}")
        println("Password: ${password.toByteArray().toHexString()}")
        assert(password.toByteArray().contentEquals(expectedMessage))
        val messageWithHeader = HashedPasswordMessage.from("postgres", "postgres", md5Salt)
        return MessageOrBytes(messageWithHeader, null)
    }

    private fun parseResponse(byteArray: ByteArray): ByteArray {
        if (byteArray[0] == 'R'.code.toByte()) {
            // AuthenticationMD5Password
            if (byteArray[4] == 0x0c.toByte() && byteArray[8] == 0x05.toByte()) {
                println("AuthenticationMD5Password")
                md5Salt = byteArray.copyOfRange(9, 13)
                println(
                    "md5Salt: ${md5Salt.joinToString(separator = " ")
                        { byte -> "%02x".format(byte) }}",
                )
            }
            val auth = String(byteArray.copyOfRange(5, byteArray.size - 1), Charset.forName("UTF-8"))
            println(auth)
        }
        return byteArray
    }
}

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

open class ParsedMessage(
    open val header: Char,
    open val length: Int,
    open val originalContent: ByteArray,
) {
    override fun toString(): String {
        return "ParsedMessage(header=$header, length=$length)"
    }

    fun isTermination(): Boolean {
        return header == 'X'
    }

    open fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(5)
        buffer.put(header.code.toByte())
        buffer.putInt(length)
        return buffer.array() + originalContent
    }

    companion object {
        fun fromBytes(header: Char, length: Int, bytes: ByteArray): ParsedMessage {
            if (bytes.size < length - 4) {
                throw Exception("Not enough bytes to parse message")
            }
            println(
                "Parsing message with header $header" + bytes.joinToString(separator = " ") { byte ->
                    "%02x".format(byte)
                },
            )
            return when (header) {
                'X' -> TerminationMessage.fromBytes(bytes)
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

class TerminationMessage(
    header: Char = 'X',
    length: Int = 4,
    originalContent: ByteArray,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(bytes: ByteArray): TerminationMessage {
            val buffer = ByteBuffer.wrap(bytes)
            val header = buffer.get().toInt().toChar()
            val length = buffer.int
            return TerminationMessage(header, length, bytes)
        }
    }
}

class MessageOrBytes(
    val message: ParsedMessage?,
    val bytes: ByteArray?,
)

class QueryMessage(
    override val header: Char = 'Q',
    override val length: Int,
    originalContent: ByteArray,
    val query: String,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): QueryMessage {
            val query = String(bytes.copyOfRange(0, bytes.size - 1), Charset.forName("UTF-8"))
            println(query)
            return QueryMessage('Q', length, originalContent = bytes, query = query)
        }
    }
}

class ExecuteMessage(
    override val header: Char = 'E',
    override val length: Int,
    originalContent: ByteArray,
    val statementName: String,
    val maxRows: Int,
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
            val maxRows = buffer.int
            return ExecuteMessage('E', length, bytes, statementName, maxRows)
        }
    }

    /* override fun toByteArray(): ByteArray {
        val statementNameBytes = statementName.toByteArray()
        val bufferSize = 1 + 4 + statementNameBytes.size + 1 + 4
        val buffer = ByteBuffer.allocate(bufferSize)

        buffer.put(header.code.toByte())
        buffer.putInt(length)
        buffer.put(statementNameBytes)
        buffer.put(0.toByte())
        buffer.putInt(maxRows)

        return buffer.array()
    } */
}

class BindMessage(
    override val header: Char = 'B',
    override val length: Int,
    override val originalContent: ByteArray,
    val statementName: String,
    val portalName: String,
    val parameterFormatCodes: List<Int>,
    val parameters: List<ByteArray>,
    val resultFormatCodes: List<Int>,
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
            val portalName = String(portalNameBytes.toByteArray(), Charset.forName("UTF-8"))
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
                portalName,
                parameterFormatCodes,
                parameterValues,
                resultFormatCodes,
            )
        }
    }

    /* override fun toByteArray(): ByteArray {
        val statementNameBytes = statementName.toByteArray()
        val portalNameBytes = portalName.toByteArray()
        val parameterCount = parameterFormatCodes.size
        val parameterValues = parameters.map { it.toByteArray() }
        val resultFormatCount = resultFormatCodes.size
        val bufferSize = 1 + 4 + statementName
        val buffer = ByteBuffer.allocate(bufferSize)

        buffer.put(header.code.toByte())
        buffer.putInt(length)
        buffer.put(statementNameBytes)
        buffer.put(0.toByte())
        buffer.put(portalNameBytes)
        buffer.put(0.toByte())
        buffer.putShort(parameterCount.toShort())
        parameterFormatCodes.forEach { buffer.putShort(it.toShort()) }
        parameterValues.forEach { buffer.putInt(it.size) }
        parameterValues.forEach { buffer.put(it) }
        buffer.putShort(resultFormatCount.toShort())
        resultFormatCodes.forEach { buffer.putShort(it.toShort()) }

        return buffer.array()
     */
}

class SyncMessage(
    override val header: Char = 'S',
    override val length: Int,
) : ParsedMessage(header, length, ByteArray(0)) {
    companion object {
        fun fromBytes(length: Int): SyncMessage {
            return SyncMessage('S', length)
        }
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

    /* override fun toByteArray(): ByteArray {
        val queryBytes = query.toByteArray()
        val statementNameBytes = statementName.toByteArray()
        val parameterCount = parameterTypes.size
        val bufferSize = 1 + 4 + queryBytes.size + 1 + statementNameBytes.size + 1 + 2 + parameterCount * 4
        val buffer = ByteBuffer.allocate(bufferSize)

        buffer.put(header.code.toByte())
        buffer.putInt(length)
        buffer.put(queryBytes)
        buffer.put(0.toByte())
        buffer.put(statementNameBytes)
        buffer.put(0.toByte())
        buffer.putShort(parameterCount.toShort())
        parameterTypes.forEach { buffer.putInt(it) }

        return buffer.array()
    } */

    fun printQuery() {
        println("Query: $query")
        println("Statement name: $statementName")
        println("Parameter types: $parameterTypes")
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
            // The documentation says nothing about the last zero byte but datagrip sends it and it works,
            // so we do too ¯\_(ツ)_/¯
            val digest = MessageDigest.getInstance("MD5")
            val md5 = digest.digest(password.toByteArray() + username.toByteArray())
            val md5asString = md5.toHexString()
            val md5WithSalt = digest.digest(md5asString.toByteArray() + salt)
            val message = "md5${md5WithSalt.toHexString()}".toByteArray()
            return message
        }
    }
}

fun ByteArray.toHexString() = joinToString("") { "%02x".format(it) }
