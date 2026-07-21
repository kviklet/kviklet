package dev.kviklet.kviklet

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import java.net.URI

class WebSocketOriginInterceptorTest {

    private val wsHandler = mockk<WebSocketHandler>()

    private fun handshake(
        interceptor: WebSocketOriginInterceptor,
        requestUri: String,
        origin: String?,
    ): Pair<Boolean, HttpStatus?> {
        val headers = HttpHeaders()
        origin?.let { headers.origin = it }

        val request = mockk<ServerHttpRequest> {
            every { this@mockk.headers } returns headers
            every { uri } returns URI(requestUri)
        }
        var status: HttpStatus? = null
        val response = mockk<ServerHttpResponse> {
            every { setStatusCode(any()) } answers { status = firstArg() }
        }

        val allowed = interceptor.beforeHandshake(request, response, wsHandler, mutableMapOf())
        return allowed to status
    }

    @Test
    fun `allows same hostname regardless of scheme and port`() {
        val interceptor = WebSocketOriginInterceptor(emptyList())

        val (allowed, _) = handshake(
            interceptor,
            requestUri = "http://kviklet.example.com:8080/sql/abc",
            origin = "https://kviklet.example.com",
        )
        assertTrue(allowed)
    }

    @Test
    fun `rejects foreign origin when no origins are configured`() {
        val interceptor = WebSocketOriginInterceptor(emptyList())

        val (allowed, status) = handshake(
            interceptor,
            requestUri = "https://kviklet.example.com/sql/abc",
            origin = "https://evil.example.org",
        )
        assertFalse(allowed)
        assertTrue(status == HttpStatus.FORBIDDEN)
    }

    @Test
    fun `allows configured cors origin`() {
        val interceptor = WebSocketOriginInterceptor(listOf("http://localhost:5173"))

        val (allowed, _) = handshake(
            interceptor,
            requestUri = "http://backend.internal:8081/sql/abc",
            origin = "http://localhost:5173",
        )
        assertTrue(allowed)
    }

    @Test
    fun `allows configured wildcard origin pattern`() {
        val interceptor = WebSocketOriginInterceptor(listOf("https://*.example.com"))

        val (allowed, _) = handshake(
            interceptor,
            requestUri = "http://backend.internal:8081/sql/abc",
            origin = "https://app.example.com",
        )
        assertTrue(allowed)
    }

    @Test
    fun `rejects origin not matching configured origins`() {
        val interceptor = WebSocketOriginInterceptor(listOf("http://localhost:5173"))

        val (allowed, status) = handshake(
            interceptor,
            requestUri = "http://backend.internal:8081/sql/abc",
            origin = "https://evil.example.org",
        )
        assertFalse(allowed)
        assertTrue(status == HttpStatus.FORBIDDEN)
    }

    @Test
    fun `allows requests without origin header`() {
        val interceptor = WebSocketOriginInterceptor(emptyList())

        val (allowed, _) = handshake(
            interceptor,
            requestUri = "https://kviklet.example.com/sql/abc",
            origin = null,
        )
        assertTrue(allowed)
    }
}
