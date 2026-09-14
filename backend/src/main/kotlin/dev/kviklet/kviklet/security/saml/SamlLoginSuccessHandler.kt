// This file is not MIT licensed
package dev.kviklet.kviklet.security.saml

import dev.kviklet.kviklet.security.LoginRedirectTargetFilter
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.userFacingLoginFailureMessage
import dev.kviklet.kviklet.service.BaseUrlResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "saml", name = ["enabled"], havingValue = "true")
class SamlLoginSuccessHandler(
    private val samlUserService: SamlUserService,
    private val baseUrlResolver: BaseUrlResolver,
) : SimpleUrlAuthenticationSuccessHandler() {

    private val securityContextRepository = HttpSessionSecurityContextRepository()

    private val logger = LoggerFactory.getLogger(SamlLoginSuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authentication: Authentication?,
    ) {
        if (request == null || response == null) return

        // Convert SAML authentication to use UserDetailsWithId
        if (authentication?.principal is Saml2AuthenticatedPrincipal) {
            val samlPrincipal = authentication.principal as Saml2AuthenticatedPrincipal

            try {
                val user = samlUserService.loadUser(samlPrincipal)

                val authorities = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }
                val userDetails = UserDetailsWithId(user.getId()!!, user.email, "", authorities)

                // Create a new authentication token with UserDetailsWithId as principal
                val newAuth = UsernamePasswordAuthenticationToken(userDetails, authentication.credentials, authorities)
                val context = SecurityContextHolder.createEmptyContext()
                context.authentication = newAuth
                SecurityContextHolder.setContext(context)

                // Explicitly save to session
                securityContextRepository.saveContext(context, request, response)
            } catch (e: Exception) {
                // Clear any partial authentication and invalidate session
                SecurityContextHolder.clearContext()
                request.session?.invalidate()

                // Redirect to the login page with a reason; only messages written for the user are
                // forwarded, the cause itself stays in the log.
                logger.warn("SAML login failed", e)
                val baseUrl = baseUrlResolver.resolve(request)
                val errorMessage = java.net.URLEncoder.encode(userFacingLoginFailureMessage(e), "UTF-8")
                redirectStrategy.sendRedirect(request, response, "$baseUrl/login?error=$errorMessage")
                return
            }
        }

        val baseUrl = baseUrlResolver.resolve(request)
        // Back to the page that sent the user to the login, or the frontend's index page.
        val target = LoginRedirectTargetFilter.consume(request) ?: "/"
        redirectStrategy.sendRedirect(request, response, "$baseUrl$target")
    }
}
