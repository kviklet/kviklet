// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres.messages

fun tlsNotSupportedMessage(): ByteArray = "N".toByteArray()

fun tlsSupportedMessage(): ByteArray = "S".toByteArray()

// GSSAPI encryption is declined the same way an unsupported SSLRequest is: a single 'N' byte, after
// which the client falls back to sending a plain StartupMessage.
fun gssEncNotSupportedMessage(): ByteArray = "N".toByteArray()

fun isSSLRequest(byteArray: ByteArray): Boolean = hasRequestCode(byteArray, length = 0x08, code3 = 0x2f)

// CancelRequest (code 80877102) is 16 bytes: length, code, backend pid, secret key.
fun isCancelRequest(byteArray: ByteArray): Boolean = hasRequestCode(byteArray, length = 0x10, code3 = 0x2e)

// GSSENCRequest (code 80877104) is an 8 byte length + code, like SSLRequest.
fun isGSSENCRequest(byteArray: ByteArray): Boolean = hasRequestCode(byteArray, length = 0x08, code3 = 0x30)

// The startup-phase requests share a 4 byte length followed by the request code 0x04d216__, where the
// final byte distinguishes them. Matching the length as well avoids misreading a normal message.
private fun hasRequestCode(byteArray: ByteArray, length: Int, code3: Int): Boolean = byteArray.size >= 8 &&
    byteArray[0] == 0x00.toByte() &&
    byteArray[1] == 0x00.toByte() &&
    byteArray[2] == 0x00.toByte() &&
    byteArray[3] == length.toByte() &&
    byteArray[4] == 0x04.toByte() &&
    byteArray[5] == 0xd2.toByte() &&
    byteArray[6] == 0x16.toByte() &&
    byteArray[7] == code3.toByte()
