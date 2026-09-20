package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.UserDetailsWithId
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication
import org.springframework.stereotype.Component

/**
 * Counts logins through every provider from the one event Spring Security publishes for all of
 * them, instead of touching each login handler. The security context is not populated yet when the
 * event fires, so the user id is taken from the authentication itself.
 */
@Component
@Lazy(false)
class LoginTelemetryListener(private val telemetry: Telemetry) {

    @EventListener
    fun onAuthenticationSuccess(event: AuthenticationSuccessEvent) {
        val authentication = event.authentication
        val method = when (authentication) {
            is OAuth2LoginAuthenticationToken, is OAuth2AuthenticationToken -> LoginMethod.OIDC
            is Saml2Authentication -> LoginMethod.SAML
            else -> LoginMethod.PASSWORD
        }
        telemetry.track(UserLoggedIn(method), userId = userId(authentication))
    }

    private fun userId(authentication: Authentication): String? = when (val principal = authentication.principal) {
        is UserDetailsWithId -> principal.id
        is KvikletOAuthPrincipal -> principal.getUserDetails().id
        else -> null
    }
}
