package dev.kviklet.kviklet.proxy.postgres.messages

import java.nio.ByteBuffer

fun authenticationOk(): ByteArray {
    val responseBuffer = ByteBuffer.allocate(9)
    responseBuffer.put('R'.code.toByte())
    responseBuffer.putInt(8)
    responseBuffer.putInt(0)
    return responseBuffer.array()
}

// SASL auth
// Readings https://www.improving.com/thoughts/making-sense-of-scram-sha-256-authentication-in-mongodb/
// https://www.postgresql.org/docs/current/sasl-authentication.html
fun createAuthenticationSASLStartMessage(): ByteArray {
    val mechanismName = "SCRAM-SHA-256".toByteArray() + byteArrayOf(0x00)
    val msgLen = 1 + 4 + 4 + mechanismName.size // note 4 is int size
    val responseBuffer = ByteBuffer.allocate(msgLen + 1) // as msgLen size doesnt include the header
    responseBuffer.put('R'.code.toByte())
    responseBuffer.putInt(msgLen)
    responseBuffer.putInt(10)
    responseBuffer.put(mechanismName)
    return responseBuffer.array()
}

fun createAuthenticationSASLContinue(msg: ByteArray): ByteArray {
    val msgLen = 4 + 4 + msg.size // note 4 is int size
    val responseBuffer = ByteBuffer.allocate(msgLen + 1) // as msgLen size doesnt include the header
    responseBuffer.put('R'.code.toByte())
    responseBuffer.putInt(msgLen)
    responseBuffer.putInt(11)
    responseBuffer.put(msg)
    return responseBuffer.array()
}

fun createAuthenticationSASLFinal(msg: ByteArray): ByteArray {
    val msgLen = 4 + 4 + msg.size // note 4 is int size
    val responseBuffer = ByteBuffer.allocate(msgLen + 1) // as msgLen size doesnt include the header
    responseBuffer.put('R'.code.toByte())
    responseBuffer.putInt(msgLen)
    responseBuffer.putInt(12)
    responseBuffer.put(msg)
    return responseBuffer.array()
}

class SASLInitialResponse(
    override val header: Char = 'p',
    override val length: Int,
    override val originalContent: ByteArray,
    val saslMessage: String,
) : ParsedMessage(header, length, originalContent) {
    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): SASLInitialResponse {
            val buffer = ByteBuffer.wrap(bytes)
            val messageBytes = ByteArray(length)
            buffer.get(messageBytes)
            // NOTE: The message parsing below will work as long as only SCRAM-SHA-256 is supported. Once SCRAM-SHA-256-PLUS is added it won't work
            val message = String(messageBytes).subSequence(26, length).toString()
            return SASLInitialResponse('p', length, bytes, message)
        }
    }

    fun getClientNonce(): String = saslMessage.split(',')[1].replace("r=", "")
}

class SASLResponse(
    override val header: Char = 'p',
    override val length: Int,
    override val originalContent: ByteArray,
    val saslMessage: String,
) : ParsedMessage(header, length, originalContent) {

    companion object {
        fun fromBytes(length: Int, bytes: ByteArray): SASLResponse {
            val buffer = ByteBuffer.wrap(bytes)
            val messageBytes = ByteArray(length)
            buffer.get(messageBytes)
            return SASLResponse('p', length, bytes, String(messageBytes.copyOfRange(5, length)))
        }
    }
    fun getResponseWithoutProof(): String = saslMessage.split(',').subList(0, 2).joinToString(",")
    fun getProof(): String = saslMessage.split(',')[2].replaceFirst("p=", "")
}
