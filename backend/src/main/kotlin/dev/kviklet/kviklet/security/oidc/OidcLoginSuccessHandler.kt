package dev.kviklet.kviklet.security.oidc

import dev.kviklet.kviklet.security.FrontendUrlResolver
import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.transaction.Transactional
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
class OidcLoginSuccessHandler(private val frontendUrlResolver: FrontendUrlResolver) :
    SimpleUrlAuthenticationSuccessHandler() {

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

        val baseUrl = request?.let { frontendUrlResolver.resolve(it) }
        val redirectUrl = "$baseUrl/requests"
        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }
}
