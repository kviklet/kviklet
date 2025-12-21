package dev.kviklet.kviklet.security.oidc

import dev.kviklet.kviklet.security.UserDetailsWithId
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
        // Convert OIDC authentication to use UserDetailsWithId as principal
        if (authentication?.principal is OidcUser) {
            val oidcUser = authentication.principal as OidcUser
            val userDetails = oidcUser.getUserDetails()
            val newAuth = UsernamePasswordAuthenticationToken(
                userDetails,
                authentication.credentials,
                userDetails.authorities,
            )
            SecurityContextHolder.getContext().authentication = newAuth
        }

        val baseUrl = request?.let { getBaseUrl(it) }
        val redirectUrl = "$baseUrl/requests"
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun getBaseUrl(request: HttpServletRequest): String {
        val scheme = request.scheme
        val serverName = request.serverName
        val serverPort = request.serverPort

        return "$scheme://$serverName${if (serverPort != 80 && serverPort != 443) ":5173" else ""}"
    }
}
