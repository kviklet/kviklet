package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Ends the browser session of a user who has been deactivated since they logged in.
 *
 * Login-time checks alone are not enough: a deactivated user may still hold a valid session, and
 * sessions are stored outside the user record. The store is also purged on deactivation (see
 * [UserDeactivationListener]), but a request of the user may already have loaded its security
 * context from the session before that purge, and the purge depends on the indexed principal name.
 * This filter is the guarantee: every request in the session-based chain re-checks the flag with a
 * single indexed query and, for an inactive user, invalidates the session and answers 401 through
 * the chain's entry point, so the frontend returns to the login page.
 *
 * Deliberately not part of the API key chain: API keys are programmatic credentials that outlive
 * the account of whoever created them.
 */
class ActiveUserFilter(private val userAdapter: UserAdapter, private val entryPoint: AuthenticationEntryPoint) :
    OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is UserDetailsWithId && !userAdapter.isActive(principal.id)) {
            SecurityContextHolder.clearContext()
            request.getSession(false)?.invalidate()
            entryPoint.commence(request, response, AccountDeactivatedException())
            return
        }
        chain.doFilter(request, response)
    }
}
