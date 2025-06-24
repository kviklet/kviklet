package dev.kviklet.kviklet.proxy.postgres.messages

fun tlsNotSupportedMessage(): ByteArray {
    return "N".toByteArray()
}

fun tlsSupportedMessage(): ByteArray {
    return "S".toByteArray()
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