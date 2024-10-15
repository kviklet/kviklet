package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.db.ExecutePayload
import dev.kviklet.kviklet.service.EventService
import dev.kviklet.kviklet.service.dto.ExecutionRequest
import dev.kviklet.kviklet.service.dto.utcTimeNow
import org.postgresql.core.PGStream
import org.postgresql.core.QueryExecutorBase
import org.postgresql.core.v3.ConnectionFactoryImpl
import org.postgresql.util.HostSpec
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

class Connection(
    val clientSocket: Socket,
    var targetSocket: Socket,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
    private val username: String,
    private val password: String,

) {
    var clientInput: InputStream = clientSocket.getInputStream()
    var clientOutput: OutputStream = clientSocket.getOutputStream()
    var targetInput: InputStream = targetSocket.getInputStream()
    var targetOutput: OutputStream = targetSocket.getOutputStream()

    var targetHost: String = ""
    var targetPort: Int = 5432
    var params: Map<String, String> = emptyMap()

    private var md5Salt = byteArrayOf()

    private val boundStatements: MutableMap<String, Statement> = mutableMapOf()

    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"

    fun startHandling(
        targetHost: String,
        targetPort: Int,
        proxyUsername: String,
        proxyPassword: String,
        passThrough: Boolean = false,
        params: Map<String, String>,
    ) {
        this.proxyUsername = proxyUsername
        this.proxyPassword = proxyPassword
        this.targetHost = targetHost
        this.targetPort = targetPort
        this.clientInput = clientSocket.getInputStream()
        this.clientOutput = clientSocket.getOutputStream()
        this.targetInput = targetSocket.getInputStream()
        this.targetOutput = targetSocket.getOutputStream()
        this.params = params

        val clientBuffer = ByteArray(8192)
        val targetBuffer = ByteArray(8192)
        var lastClientMessage: MessageOrBytes? = null

        while (true) {
            if (clientInput.available() > 0) {
                val bytesRead = clientInput.read(clientBuffer)
                val data = clientBuffer.copyOf(bytesRead)
                if (passThrough) {
                    targetOutput.write(data, 0, data.size)
                    targetOutput.flush()
                } else {
                    for (clientMessage in parseDataToMessages(data)) {
                        val newData = clientMessage.message?.toByteArray() ?: clientMessage.bytes!!
                        if (clientMessage.response != null) {
                            clientOutput.write(clientMessage.response, 0, clientMessage.response.size)
                            clientOutput.flush()
                        } else {
                            targetOutput.write(newData, 0, newData.size)
                            targetOutput.flush()
                            lastClientMessage = clientMessage
                        }
                    }
                }
            }
            if (passThrough) {
                if (targetInput.available() > 0) {
                    val bytesRead = targetInput.read(targetBuffer)
                    val data = targetBuffer.copyOf(bytesRead)
                    clientOutput.write(data, 0, data.size)
                    clientOutput.flush()
                }
            } else {
                val singleByte = ByteArray(1)
                val bytesRead: Int = try {
                    targetInput.read(singleByte, 0, 1)
                } catch (e: SocketTimeoutException) {
                    0 // No data available
                }

                if (bytesRead > 0) {
                    // Data is available, prepend the read byte to the buffer
                    val availableBytes = targetInput.available()
                    var dataBuffer = ByteArray(availableBytes + 1)
                    System.arraycopy(singleByte, 0, dataBuffer, 0, 1)
                    if (availableBytes > 0) {
                        targetInput.read(dataBuffer, 1, availableBytes)
                    }

                    // Handle the data as before
                    var responseData = parseResponse(dataBuffer)
                    if (responseData.second == "SSL") {
                        dataBuffer = "N".toByteArray()
                    }
                    clientOutput.write(dataBuffer, 0, dataBuffer.size)
                    clientOutput.flush()
                }
            }

            if (lastClientMessage?.message?.isTermination() == true) {
                break
            }
        }
    }

    private fun printBytesAsHexAndUTF8(bytes: ByteArray, prefix: String) {
        println(
            "$prefix: ${bytes.joinToString(" ") { "%02x".format(it) }} - ${String(bytes, Charset.forName("UTF-8"))}",
        )
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
            // return N for no SSL
            val nByteArray = "N".toByteArray()
            return listOf(MessageOrBytes(null, byteArray, nByteArray))
        } else if (
            byteArray[0] == 0x00.toByte()
        ) {
            // return authentication okay as if we are in trust mode
            val responseBuffer = ByteBuffer.allocate(13)
            responseBuffer.put('R'.code.toByte())
            responseBuffer.putInt(12)
            responseBuffer.putInt(5)
            val salt = Random.nextInt(0, 10000)
            responseBuffer.putInt(salt)
            md5Salt = ByteBuffer.allocate(4).putInt(salt).array()
            val okByteArray = responseBuffer.array()
            val authMessage = MessageOrBytes(null, byteArray, okByteArray)
            return listOf(authMessage)
        }
        val messages = mutableListOf<MessageOrBytes>()
        while (buffer.remaining() > 0) {
            val header = buffer.get().toInt().toChar()
            val length = buffer.int
            val messageBytes = ByteArray(length - 4)
            buffer.get(messageBytes)
            val parsedMessage = ParsedMessage.fromBytes(header, length, messageBytes)

            if (parsedMessage is HashedPasswordMessage) {
                confirmPasswordMessage(parsedMessage)
                messages.add(
                    MessageOrBytes(
                        null,
                        parsedMessage.originalContent,
                        authenticationOk(),
                    ),
                )

                for (param in params) {
                    messages.add(
                        MessageOrBytes(
                            null,
                            parsedMessage.originalContent,
                            paramMessage(param.key, param.value),
                        ),
                    )
                }
                // param end message BackendKeyData
                messages.add(
                    MessageOrBytes(
                        null,
                        parsedMessage.originalContent,
                        backendKeyData(),
                    ),
                )
                messages.add(MessageOrBytes(null, byteArray, readyForQuery()))
                continue
            }
            if (parsedMessage is ParseMessage) {
                boundStatements[parsedMessage.statementName] = Statement(
                    parsedMessage.query,
                    parameterTypes = parsedMessage.parameterTypes,
                )
            }
            if (parsedMessage is BindMessage) {
                val statement = boundStatements[parsedMessage.statementName]!!
                boundStatements[parsedMessage.statementName] = Statement(
                    statement.query,
                    parsedMessage.parameterFormatCodes,
                    statement.parameterTypes,
                    parsedMessage.parameters,
                )
            }
            if (parsedMessage is ExecuteMessage) {
                val statement = boundStatements[parsedMessage.statementName]!!
                val executePayload = ExecutePayload(
                    query = statement.interpolateQuery(),
                )
                eventService.saveEvent(
                    executionRequest.id!!,
                    userId,
                    executePayload,
                )
            }

            messages.add(MessageOrBytes(parsedMessage, null))
        }
        return messages
    }

    private fun authenticationOk(): ByteArray {
        val responseBuffer = ByteBuffer.allocate(9)
        responseBuffer.put('R'.code.toByte())
        responseBuffer.putInt(8)
        responseBuffer.putInt(0)
        return responseBuffer.array()
    }

    private fun paramMessage(key: String, value: String): ByteArray {
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

    private fun backendKeyData(): ByteArray {
        val responseBuffer = ByteBuffer.allocate(13)
        responseBuffer.put('K'.code.toByte())
        responseBuffer.putInt(12)
        responseBuffer.putInt(0)
        responseBuffer.putInt(0)
        return responseBuffer.array()
    }

    private fun readyForQuery(): ByteArray {
        val responseBuffer = ByteBuffer.allocate(6)
        responseBuffer.put('Z'.code.toByte())
        responseBuffer.putInt(5)
        responseBuffer.put('I'.code.toByte())
        return responseBuffer.array()
    }

    private fun confirmPasswordMessage(message: HashedPasswordMessage) {
        val password = message.message
        val expectedMessage = HashedPasswordMessage.passwordContent(this.proxyUsername, this.proxyPassword, md5Salt)
        if (!password.toByteArray().contentEquals(expectedMessage)) {
            throw Exception("Password does not match")
        }
    }

    private fun parseResponse(byteArray: ByteArray): Pair<ByteArray, String> = byteArray to "Not handled"
}

class Statement(
    val query: String,
    val parameterFormatCodes: List<Int> = mutableListOf(),
    val parameterTypes: List<Int> = mutableListOf(),
    val boundParams: List<ByteArray> = mutableListOf(),
) {
    override fun toString(): String =
        "Statement(query='$query', parameterFormatCodes=$parameterFormatCodes, boundParams=$boundParams)," +
            "interpolated query: ${interpolateQuery()}"

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
    private val databaseName: String,
    private val username: String,
    private val password: String,
    private val eventService: EventService,
    private val executionRequest: ExecutionRequest,
    private val userId: String,
) {
    private var proxyUsername = "postgres"
    private var proxyPassword = "postgres"

    fun startServer(
        port: Int,
        proxyUsername: String,
        proxyPassword: String,
        startTime: LocalDateTime,
        maxTimeMinutes: Long,
    ) {
        this.proxyUsername = proxyUsername
        this.proxyPassword = proxyPassword

        val maxConnections = 5
        var currentConnections = 0
        val threadPool = Executors.newCachedThreadPool()
        ServerSocket(port).use { serverSocket ->

            while (true) {
                if (utcTimeNow().isAfter(startTime.plusMinutes(maxTimeMinutes))) {
                    // kill all running threads and close sockets
                    threadPool.shutdownNow()
                    serverSocket.close()
                    break
                }
                if (currentConnections >= maxConnections) {
                    Thread.sleep(1000)
                    continue
                }
                val clientSocket = serverSocket.accept()
                currentConnections++

                threadPool.submit {
                    try {
                        handleClient(clientSocket)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        if (!clientSocket.isClosed) {
                            clientSocket.close()
                        }
                        currentConnections--
                    }
                }
            }
        }
    }

    private fun handleClient(clientSocket: Socket) {
        // {application_name=PostgreSQL JDBC Driver, client_encoding=UTF8, DateStyle=ISO, MDY, integer_datetimes=on, IntervalStyle=postgres, is_superuser=on, server_encoding=UTF8, server_version=9.6.24, session_authorization=postgres, standard_conforming_strings=on, TimeZone=Europe/Berlin}
        val passThroughMode = false
        clientSocket.use { socket ->

            val targetSocket: Socket
            val params: Map<String, String>

            if (!passThroughMode) {
                val hostSpec = HostSpec(targetHost, targetPort)
                val factory = ConnectionFactoryImpl()
                val props = Properties()
                props.setProperty("user", username)
                props.setProperty("password", password)
                val database = if (databaseName != "") databaseName else username
                props.setProperty("PGDBNAME", database)
                val queryExecutor = factory.openConnectionImpl(arrayOf(hostSpec), props) as QueryExecutorBase

                val queryExecutorClass = QueryExecutorBase::class

                // Find the property by name
                val pgStreamProperty = queryExecutorClass.memberProperties.firstOrNull { it.name == "pgStream" }
                    ?: throw NoSuchElementException("Property 'pgStream' is not found")

                // Make the property accessible
                pgStreamProperty.isAccessible = true

                // Get the property value
                val pgStream = pgStreamProperty.get(queryExecutor) as PGStream
                params = queryExecutor.getParameterStatuses()
                targetSocket = pgStream.socket
            } else {
                targetSocket = Socket(targetHost, targetPort)
                params = mapOf()
            }

            targetSocket.use { forwardSocket ->

                forwardSocket.soTimeout = 10

                Connection(
                    socket,
                    forwardSocket,
                    eventService,
                    executionRequest,
                    userId,
                    username,
                    password,
                ).startHandling(
                    targetHost,
                    targetPort,
                    proxyUsername,
                    proxyPassword,
                    passThroughMode,
                    params,
                )
            }
        }
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
