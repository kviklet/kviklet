package dev.kviklet.kviklet

import org.junit.jupiter.api.Test
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.security.MessageDigest
import kotlin.experimental.and

class ProxyTest {

    @Test
    fun startServer() {
        val server = TcpServer("localhost", 5432)
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

class TcpServer(private val targetHost: String, private val targetPort: Int) {

    private var md5Salt = byteArrayOf()
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

        while (true) {
            if (clientInput.available() > 0) {
                val bytesRead = clientInput.read(clientBuffer)
                val data = clientBuffer.copyOf(bytesRead)
                val newData = parseData(data)
                val hexData = data.joinToString(separator = " ") { byte -> "%02x".format(byte) }
                val stringData = String(clientBuffer, 0, bytesRead, Charset.forName("UTF-8"))
                println(
                    "Client to Target: $hexData [${stringData.filter { it.isLetterOrDigit() || it.isWhitespace() }}]",
                )
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

            // Sleep briefly to prevent a tight loop that consumes too much CPU
            // You can adjust the sleep duration to balance responsiveness and CPU usage
            Thread.sleep(10)
        }
    }

    private fun parseData(byteArray: ByteArray): ByteArray {
        if (byteArray[0] == 0x50.toByte() && byteArray[1] == 0x00.toByte()) {
            println("Query")
            val query = String(byteArray.copyOfRange(5, byteArray.size - 1), Charset.forName("UTF-8"))
            println(query)
        } else if (
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
        } else if (
            byteArray[0] == 0x00.toByte()
        ) {
            println("Startup")
        } else if (
            byteArray[0] == 0x70.toByte()
        ) {
            println("Password")
            // log the password and replace with hardcoded password
            val password = String(byteArray.copyOfRange(5, byteArray.size - 1), Charset.forName("UTF-8"))
            println(password)
            val digest = MessageDigest.getInstance("MD5")
            val newPassword = "postgrespostgres"
            println(
                "Using password: $newPassword and salt: ${md5Salt.joinToString(separator = " ") { byte ->
                    "%02x".format(
                        byte,
                    )
                }}",
            )
            val md5 = digest.digest(newPassword.toByteArray())
            val md5asString = md5.joinToString(
                separator = "",
            ) { byte -> "%02x".format(byte) }
            val md5WithSalt = digest.digest(md5asString.toByteArray() + md5Salt)
            // format bytes to md5 + the hashed and salted password
            val message = "md5${md5WithSalt.joinToString(
                separator = "",
            ) { byte -> "%02x".format(byte) }}".toByteArray(Charset.forName("UTF-8"))
            val length = ByteBuffer.allocate(4).putInt(message.size + 4).array()
            val messageWithHeader = "p".toByteArray() + length + message
            return messageWithHeader
        }
        return byteArray
    }

    private fun parseResponse(byteArray: ByteArray): ByteArray {
        if (byteArray[0] == 0x52.toByte()) {
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
