// This file is not MIT licensed
package dev.kviklet.kviklet.security.saml

import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserDetailsWithId
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.transaction.Transactional
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "saml", name = ["enabled"], havingValue = "true")
class SamlLoginSuccessHandler(private val samlUserService: SamlUserService) :
    SimpleUrlAuthenticationSuccessHandler() {

    @Transactional
    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        // Convert SAML authentication to use UserDetailsWithId
        if (authentication?.principal is Saml2AuthenticatedPrincipal) {
            val samlPrincipal = authentication.principal as Saml2AuthenticatedPrincipal
            val user = samlUserService.loadUser(samlPrincipal)

            val authorities = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }
            val userDetails = UserDetailsWithId(user.getId()!!, user.email, "", authorities)

            // Create a new authentication token with UserDetailsWithId as principal
            val newAuth = UsernamePasswordAuthenticationToken(userDetails, authentication.credentials, authorities)
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
