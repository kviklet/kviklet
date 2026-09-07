package dev.kviklet.kviklet.security.oidc

import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.LoginRedirectTargetFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.transaction.Transactional
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OidcLoginSuccessHandler : SimpleUrlAuthenticationSuccessHandler() {

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        // Convert OAuth/OIDC authentication to use UserDetailsWithId as principal
        val principal = authentication?.principal
        if (principal is KvikletOAuthPrincipal) {
            val userDetails = principal.getUserDetails()
            val newAuth = UsernamePasswordAuthenticationToken(
                userDetails,
                authentication.credentials,
                userDetails.authorities,
            )
            SecurityContextHolder.getContext().authentication = newAuth
        }

        val baseUrl = request?.let { getBaseUrl(it) }
        // Back to the page that sent the user to the login, or the frontend's index page.
        val target = request?.let { LoginRedirectTargetFilter.consume(it) } ?: "/"
        redirectStrategy.sendRedirect(request, response, "$baseUrl$target")
    }

    private fun getBaseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort

        return "$scheme://$serverName${if (serverPort != 80 && serverPort != 443) ":5173" else ""}"
    }
}
