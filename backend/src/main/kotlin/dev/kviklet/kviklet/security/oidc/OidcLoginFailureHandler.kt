package dev.kviklet.kviklet.security.oidc

import dev.kviklet.kviklet.security.userFacingLoginFailureMessage
import dev.kviklet.kviklet.service.BaseUrlResolver
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder

/**
 * Sends a failed OAuth2/OIDC login back to the frontend login page with the reason, the same way
 * the SAML handler does, so that e.g. a deactivated user learns why they were refused instead of
 * landing on a bare backend error page. Only messages Kviklet wrote for the user are forwarded;
 * the actual exception stays in the log.
 */
@Component
class OidcLoginFailureHandler(private val baseUrlResolver: BaseUrlResolver) :
    SimpleUrlAuthenticationFailureHandler() {

    private val logger = LoggerFactory.getLogger(OidcLoginFailureHandler::class.java)

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        logger.warn("OAuth2 login failed", exception)
        val message = URLEncoder.encode(userFacingLoginFailureMessage(exception), "UTF-8")
        redirectStrategy.sendRedirect(request, response, "${baseUrlResolver.resolve(request)}/login?error=$message")
    }
}
