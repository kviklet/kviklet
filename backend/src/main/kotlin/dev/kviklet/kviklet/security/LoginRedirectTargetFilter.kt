package dev.kviklet.kviklet.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Remembers which page the user wanted to open before an SSO login.
 *
 * The frontend starts OIDC and SAML logins with a top-level navigation to
 * /oauth2/authorization/{id} or /saml2/authenticate/{id}, so it cannot keep state of its
 * own across the round trip to the identity provider. It passes the page to return to as
 * a `redirect` query parameter instead; this filter stores it in the HTTP session and the
 * login success handlers redirect there afterwards (see [consume]).
 *
 * Both of Spring's login flows already rely on the session surviving the round trip (the
 * pending authorization request lives there), so the target is at least as durable as the
 * login itself. Only relative paths are accepted; anything that could point off-site is
 * dropped so the login can't be turned into an open redirector.
 */
class LoginRedirectTargetFilter : OncePerRequestFilter() {

    companion object {
        const val REDIRECT_PARAM = "redirect"
        const val SESSION_ATTRIBUTE = "kviklet.loginRedirectTarget"
        private const val MAX_LENGTH = 2048

        private val loginStartPrefixes = listOf("/oauth2/authorization/", "/saml2/authenticate/")

        /** The target as a path relative to the frontend origin, or null if it is unusable. */
        fun sanitize(target: String?): String? {
            if (target.isNullOrEmpty() || target.length > MAX_LENGTH) return null
            // "//host" and "/\host" are treated as protocol-relative URLs by browsers.
            if (!target.startsWith("/") || target.startsWith("//") || target.startsWith("/\\")) return null
            if (target.any { it.isISOControl() }) return null
            return target
        }

        /** Returns the stored target and forgets it, so it applies to exactly one login. */
        fun consume(request: HttpServletRequest): String? {
            val session = request.getSession(false) ?: return null
            val target = session.getAttribute(SESSION_ATTRIBUTE) as? String
            if (target != null) session.removeAttribute(SESSION_ATTRIBUTE)
            return target
        }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (request.method == "GET" && loginStartPrefixes.any { request.requestURI.startsWith(it) }) {
            val target = sanitize(request.getParameter(REDIRECT_PARAM))
            if (target != null) {
                request.getSession(true).setAttribute(SESSION_ATTRIBUTE, target)
            } else {
                // A login started without a target must not pick up one left behind earlier.
                request.getSession(false)?.removeAttribute(SESSION_ATTRIBUTE)
            }
        }
        filterChain.doFilter(request, response)
    }
}
