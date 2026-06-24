package dev.kviklet.kviklet.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * MDC keys attached to structured (JSON) logs. Keeping them in one place lets the
 * HTTP filter, the websocket handler and the proxy emit the same field names, so
 * log lines correlate regardless of where they originate.
 */
object LoggingKeys {
    const val REQUEST_ID = "requestId"
    const val USER_ID = "userId"
    const val HTTP_METHOD = "httpMethod"
    const val PATH = "path"
    const val WS_SESSION_ID = "wsSessionId"
    const val EXECUTION_REQUEST_ID = "executionRequestId"
}

/**
 * Runs [block] with the given non-null key/value pairs added to the logging MDC,
 * removing exactly those keys afterwards. Safe to use on pooled threads (websocket
 * and proxy workers) where leftover MDC state would otherwise leak across tasks.
 */
inline fun <T> withLoggingContext(vararg pairs: Pair<String, String?>, block: () -> T): T {
    val addedKeys = pairs.mapNotNull { (key, value) ->
        if (value != null) {
            MDC.put(key, value)
            key
        } else {
            null
        }
    }
    try {
        return block()
    } finally {
        addedKeys.forEach(MDC::remove)
    }
}

private const val REQUEST_ID_HEADER = "X-Request-Id"
private const val MAX_REQUEST_ID_LENGTH = 64

/**
 * Populates the logging MDC for every HTTP request with a request id and, once
 * authentication has run, the calling user's id plus the method and path.
 *
 * An inbound X-Request-Id header is honoured (so a request can be correlated
 * across services / through a proxy); otherwise a fresh id is generated. The id is
 * echoed back in the response header so clients and support can reference it.
 *
 * Ordered after the Spring Security filter chain so the SecurityContext is already
 * populated by the time we read the user id.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class RequestLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = resolveRequestId(request)
        response.setHeader(REQUEST_ID_HEADER, requestId)
        withLoggingContext(
            LoggingKeys.REQUEST_ID to requestId,
            LoggingKeys.HTTP_METHOD to request.method,
            LoggingKeys.PATH to request.requestURI,
            LoggingKeys.USER_ID to currentUserId(),
        ) {
            filterChain.doFilter(request, response)
        }
    }

    private fun resolveRequestId(request: HttpServletRequest): String {
        val inbound = request.getHeader(REQUEST_ID_HEADER)?.trim()
        return if (!inbound.isNullOrEmpty()) inbound.take(MAX_REQUEST_ID_LENGTH) else UUID.randomUUID().toString()
    }

    private fun currentUserId(): String? {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return (principal as? UserDetailsWithId)?.id
    }
}
