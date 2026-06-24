package dev.kviklet.kviklet.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticationException
import org.springframework.security.web.DefaultRedirectStrategy
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.net.URLEncoder

@Component
class SsoAuthenticationFailureHandler(private val frontendUrlResolver: FrontendUrlResolver) :
    AuthenticationFailureHandler {

    private val logger = LoggerFactory.getLogger(SsoAuthenticationFailureHandler::class.java)
    private val redirectStrategy = DefaultRedirectStrategy()

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val (code, message) = extractErrorDetails(exception)
        logger.info("SSO authentication failed: code={}, message={}", code, message)

        val baseUrl = frontendUrlResolver.resolve(request)
        val encodedMessage = URLEncoder.encode(message.take(MAX_MESSAGE_LENGTH), Charsets.UTF_8)
        val encodedCode = URLEncoder.encode(code, Charsets.UTF_8)
        val redirectUrl = "$baseUrl/login?sso_error=$encodedCode&sso_message=$encodedMessage"

        redirectStrategy.sendRedirect(request, response, redirectUrl)
    }

    private fun extractErrorDetails(exception: AuthenticationException): Pair<String, String> = when (exception) {
        is OAuth2AuthenticationException -> {
            val code = exception.error?.errorCode?.takeIf { it.isNotBlank() } ?: "sso_failure"
            val description = exception.error?.description?.takeIf { it.isNotBlank() }
                ?: exception.message
                ?: "SSO login failed."
            code to description
        }

        is Saml2AuthenticationException -> {
            val code = exception.saml2Error?.errorCode?.takeIf { it.isNotBlank() } ?: "sso_failure"
            val description = exception.saml2Error?.description?.takeIf { it.isNotBlank() }
                ?: exception.message
                ?: "SAML login failed."
            code to description
        }

        else -> "sso_failure" to (exception.message ?: "SSO login failed.")
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 500
    }
}
