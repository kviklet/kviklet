package dev.kviklet.kviklet

import org.junit.jupiter.api.Test
import kotlin.experimental.and

class ProxyTest {

    @Test
    fun testbytes() {
        val byte = 0xF5.toByte()
        println(0xFF)
        println(byte)
        println(byte.toInt())
        println(byte and 0xFF.toByte())
        println(byte.toInt() and 0xFF.toInt())
    }
}
