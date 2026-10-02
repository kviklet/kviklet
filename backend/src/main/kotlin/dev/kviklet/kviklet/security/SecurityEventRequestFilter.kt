// This file is not MIT licensed
package dev.kviklet.kviklet.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/** Snapshot the socket peer before ForwardedHeaderFilter can rewrite it from untrusted headers. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityEventRequestFilter : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        request.setAttribute(PEER_ATTRIBUTE, request.remoteAddr)
        chain.doFilter(request, response)
    }

    companion object {
        const val PEER_ATTRIBUTE = "kviklet.eventPeer"
        fun fields(request: HttpServletRequest?) = mapOf(
            "source_ip" to request?.getAttribute(PEER_ATTRIBUTE),
            "http_method" to request?.method,
        )
    }
}
