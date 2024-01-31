package dev.kviklet.kviklet

import dev.kviklet.kviklet.proxy.PostgresProxy
import org.junit.jupiter.api.Test
import kotlin.experimental.and

class ProxyTest {

    @Test
    fun startServer() {
        val server = PostgresProxy("localhost", 5432)
        server.startServer(5438)
    }

    @Test
    fun testbytes() {
        val byte = 0xF5.toByte()
        println(0xFF.toInt())
        println(byte)
        println(byte.toInt())
        println(byte and 0xFF.toByte())
        println(byte.toInt() and 0xFF.toInt())
    }
}
