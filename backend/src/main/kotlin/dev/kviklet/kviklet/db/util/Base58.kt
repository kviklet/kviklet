package dev.kviklet.kviklet.db.util

import java.math.BigInteger

private val BASE: BigInteger = BigInteger.valueOf(58)
private val ALPHABET = "123456789abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray()

fun base58encode(source: ByteArray): String {
    if (source.isEmpty()) {
        return ""
    }

    val paddedSource: ByteArray = if (source[0] < 0) {
        val paddedSource = ByteArray(source.size + 1)
        System.arraycopy(source, 0, paddedSource, 1, source.size)
        paddedSource
    } else {
        source
    }

    var dividend = BigInteger(paddedSource)
    if (dividend == BigInteger.ZERO) return "1"

    val sb = StringBuilder()
    while (dividend > BigInteger.ZERO) {
        val qr = dividend.divideAndRemainder(BASE)
        sb.append(ALPHABET[qr[1].toInt()])
        dividend = qr[0]
    }

    return sb.reverse().toString()
}
