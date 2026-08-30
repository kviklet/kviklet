// This file is not MIT licensed
package dev.kviklet.kviklet.proxy

import dev.kviklet.kviklet.proxy.postgres.authenticateClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

// KVI-247 #6: the handshake budget (the caller's soTimeout) must bound the WHOLE handshake. A plain socket
// timeout resets on every byte, so a slow-loris client dribbling one byte per interval would otherwise hold
// a handshake slot indefinitely -- the deadline has to be re-checked before every read, including the
// partial reads inside a frame and the SASL exchange.
class PostgresProxyHandshakeDeadlineTest {

    private val budgetMs = 1000

    // Well past the budget, but short enough that a slow abort (a per-read timeout instead of the total
    // deadline) is caught as a failure rather than hiding behind a generous margin.
    private val maxAcceptableMs = 4000L

    // Runs authenticateClient on a background thread the way ProxyServer does: budget as soTimeout.
    private fun runHandshake(serverSide: Socket): CompletableFuture<Pair<Long, Throwable?>> {
        val outcome = CompletableFuture<Pair<Long, Throwable?>>()
        Thread {
            val start = System.currentTimeMillis()
            val thrown = runCatching {
                serverSide.soTimeout = budgetMs
                authenticateClient(serverSide, null, "placeholder-password") { null }
            }.exceptionOrNull()
            outcome.complete(Pair(System.currentTimeMillis() - start, thrown))
        }.start()
        return outcome
    }

    // Writes one byte per interval, stopping once the handshake thread has given up.
    private fun dribble(socket: Socket, bytes: ByteArray, outcome: CompletableFuture<*>) {
        val output = socket.getOutputStream()
        for (byte in bytes) {
            if (outcome.isDone) return
            try {
                output.write(byte.toInt())
                output.flush()
            } catch (e: Exception) {
                return // the server side closed on us, which is the expected abort
            }
            Thread.sleep(100)
        }
    }

    private fun assertAbortedOnDeadline(outcome: CompletableFuture<Pair<Long, Throwable?>>) {
        val (elapsed, thrown) = outcome.get(30, TimeUnit.SECONDS)
        assertNotNull(thrown, "expected the handshake to be aborted")
        assertTrue(
            thrown!!.message?.contains("deadline") == true,
            "expected a deadline abort, got: ${thrown::class.simpleName}: ${thrown.message}",
        )
        assertTrue(
            elapsed < maxAcceptableMs,
            "handshake held its slot for ${elapsed}ms against a ${budgetMs}ms budget",
        )
    }

    @Test
    fun `a client dribbling its startup message byte by byte cannot outlast the handshake deadline`() {
        ServerSocket(0).use { listener ->
            Socket("localhost", listener.localPort).use { client ->
                val serverSide = listener.accept()
                val outcome = runHandshake(serverSide)
                // Declare a 500-byte startup message but dribble only the first bytes of it, one every
                // 100ms: each byte resets a plain per-read timeout, so only a re-checked total deadline
                // ends this within the budget.
                val frame = ByteBuffer.allocate(60)
                frame.putInt(500)
                frame.putInt(196608)
                dribble(client, frame.array(), outcome)
                assertAbortedOnDeadline(outcome)
            }
        }
    }

    @Test
    fun `a client dribbling its SASL response byte by byte cannot outlast the handshake deadline`() {
        ServerSocket(0).use { listener ->
            Socket("localhost", listener.localPort).use { client ->
                val serverSide = listener.accept()
                val outcome = runHandshake(serverSide)

                // Complete the startup phase promptly, so the dribbling happens inside the SASL exchange.
                client.getOutputStream().apply {
                    write(startupMessage("someUser"))
                    flush()
                }
                client.soTimeout = 5000
                client.getInputStream().read(ByteArray(64)) // the AuthenticationSASL request

                // Declare a 200-byte SASL response, then dribble its first bytes one every 100ms.
                val frame = ByteBuffer.allocate(40)
                frame.put('p'.code.toByte())
                frame.putInt(200)
                frame.put("SCRAM-SHA-256".toByteArray(Charsets.UTF_8))
                dribble(client, frame.array(), outcome)
                assertAbortedOnDeadline(outcome)
            }
        }
    }

    private fun startupMessage(user: String): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("user".toByteArray(Charsets.UTF_8))
        body.write(0)
        body.write(user.toByteArray(Charsets.UTF_8))
        body.write(0)
        body.write(0)
        val bodyBytes = body.toByteArray()
        val buffer = ByteBuffer.allocate(8 + bodyBytes.size)
        buffer.putInt(8 + bodyBytes.size)
        buffer.putInt(196608) // protocol 3.0
        buffer.put(bodyBytes)
        return buffer.array()
    }
}
