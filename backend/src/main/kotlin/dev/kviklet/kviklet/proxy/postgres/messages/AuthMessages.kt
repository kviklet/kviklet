// This file is not MIT licensed
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
        // bytes is the message body (header byte and int32 length already stripped, as the MessageFramer
        // delivers it): a mechanism-name cstring, an int32 SASL-data length, then the SASL data.
        fun fromBytes(length: Int, bytes: ByteArray): SASLInitialResponse {
            val buffer = ByteBuffer.wrap(bytes)
            val mechanism = readCString(buffer)
            if (mechanism != "SCRAM-SHA-256") {
                throw Exception("Unsupported SASL mechanism '$mechanism'; only SCRAM-SHA-256 is supported")
            }
            val saslDataLength = buffer.int
            // The length is attacker-controlled and this runs pre-auth, so bound it against what the packet
            // actually contains before allocating; otherwise ByteArray(saslDataLength) is an unauthenticated
            // heap-pressure DoS (and a huge value throws OutOfMemoryError, an Error that slips past catch).
            if (saslDataLength < 0 || saslDataLength > buffer.remaining()) {
                throw Exception("SASL data length $saslDataLength exceeds the message size")
            }
            val saslData = ByteArray(saslDataLength)
            buffer.get(saslData)
            // Strip the gs2 header (e.g. "n,,") to get the SCRAM client-first-bare ("n=...,r=...").
            val clientFirstBare = stripGs2Header(String(saslData, Charsets.UTF_8))
            return SASLInitialResponse('p', length, bytes, clientFirstBare)
        }

        // The gs2 header is a channel-binding flag, an optional authzid and two commas; the client-first-bare
        // follows the second comma. Validating the shape beats the previous hardcoded offset of 26.
        private fun stripGs2Header(saslData: String): String {
            val firstComma = saslData.indexOf(',')
            val secondComma = if (firstComma >= 0) saslData.indexOf(',', firstComma + 1) else -1
            if (secondComma < 0) {
                throw Exception("Malformed SASL initial response: missing gs2 header")
            }
            return saslData.substring(secondComma + 1)
        }
    }

    fun getClientNonce(): String = saslMessage.split(',').first { it.startsWith("r=") }.removePrefix("r=")
}

class SASLResponse(
    override val header: Char = 'p',
    override val length: Int,
    override val originalContent: ByteArray,
    val saslMessage: String,
) : ParsedMessage(header, length, originalContent) {

    companion object {
        // bytes is the message body (header byte and int32 length already stripped): the SCRAM
        // client-final message, "c=<channel-binding>,r=<nonce>,p=<proof>".
        fun fromBytes(length: Int, bytes: ByteArray): SASLResponse =
            SASLResponse('p', length, bytes, String(bytes, Charsets.UTF_8))
    }
    fun getResponseWithoutProof(): String = saslMessage.split(',').subList(0, 2).joinToString(",")
    fun getProof(): String = saslMessage.split(',')[2].replaceFirst("p=", "")
}
