package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.ldap.LdapUserDetailsWithId
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
        // A SAML authentication still carries the raw assertion here; Kviklet resolves (or creates) its own
        // user only afterwards, in SamlLoginSuccessHandler, which reports the login itself.
        if (authentication is Saml2Authentication) return
        telemetry.track(UserLoggedIn(methodOf(authentication)), userId = userId(authentication))
    }

    private fun methodOf(authentication: Authentication): LoginMethod = when {
        authentication is OAuth2LoginAuthenticationToken -> LoginMethod.OIDC

        authentication is OAuth2AuthenticationToken -> LoginMethod.OIDC

        // LDAP and local logins share a token type; only the principal tells them apart.
        authentication.principal is LdapUserDetailsWithId -> LoginMethod.LDAP

        else -> LoginMethod.PASSWORD
    }

    private fun userId(authentication: Authentication): String? = when (val principal = authentication.principal) {
        is UserDetailsWithId -> principal.id
        is KvikletOAuthPrincipal -> principal.getUserDetails().id
        else -> null
    }
}
