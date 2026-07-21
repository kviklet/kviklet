package dev.kviklet.kviklet.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * CSRF protection for the session-cookie authenticated filter chain.
 *
 * Instead of synchronizer tokens, state-changing requests must carry a custom header.
 * Browsers cannot attach custom headers to form submissions or top-level navigations,
 * and a cross-site fetch() with a custom header triggers a CORS preflight that fails
 * unless the origin is explicitly allowed. This keeps the session cookie from being
 * usable cross-site even with SameSite=None (required for SAML).
 *
 * Login-flow endpoints are exempt because they receive plain browser navigations
 * (IdP redirects and form posts) which can never carry the header.
 */
class CsrfHeaderFilter : OncePerRequestFilter() {

    companion object {
        const val CSRF_HEADER_NAME = "X-Kviklet-Request"
    }

    private val safeMethods = setOf("GET", "HEAD", "OPTIONS", "TRACE")

    private val exemptPathPrefixes = listOf(
        "/login",
        "/saml2/",
        "/oauth2",
        "/logout/saml2",
    )

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (requiresHeader(request) && request.getHeader(CSRF_HEADER_NAME) == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Missing $CSRF_HEADER_NAME header")
            return
        }
        filterChain.doFilter(request, response)
    }

    private fun requiresHeader(request: HttpServletRequest): Boolean {
        if (request.method in safeMethods) {
            return false
        }
        return exemptPathPrefixes.none { request.requestURI.startsWith(it) }
    }
}
