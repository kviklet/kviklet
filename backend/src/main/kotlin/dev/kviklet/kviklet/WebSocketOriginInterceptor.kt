package dev.kviklet.kviklet

import org.springframework.http.HttpStatus
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.net.URI

/**
 * Validates the Origin header of WebSocket handshakes, since the session cookie
 * authenticates them and browsers attach it cross-site when SameSite=None (SAML).
 *
 * An origin is accepted if its hostname matches the request's hostname (the same
 * boundary cookie SameSite policies use, and robust behind reverse proxies that
 * rewrite ports/schemes) or if it matches a configured CORS origin pattern.
 * Requests without an Origin header (non-browser clients) are allowed; browsers
 * always send Origin on WebSocket handshakes.
 */
class WebSocketOriginInterceptor(allowedOrigins: List<String>) : HandshakeInterceptor {

    private val corsConfiguration = CorsConfiguration().apply {
        allowedOriginPatterns = allowedOrigins
    }

    override fun beforeHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        attributes: MutableMap<String, Any>,
    ): Boolean {
        val origin = request.headers.origin ?: return true

        val originHost = runCatching { URI(origin).host }.getOrNull()
        if (originHost != null && originHost.equals(request.uri.host, ignoreCase = true)) {
            return true
        }
        if (corsConfiguration.checkOrigin(origin) != null) {
            return true
        }

        response.setStatusCode(HttpStatus.FORBIDDEN)
        return false
    }

    override fun afterHandshake(
        request: ServerHttpRequest,
        response: ServerHttpResponse,
        wsHandler: WebSocketHandler,
        exception: Exception?,
    ) {
        // No action needed after handshake
    }
}
