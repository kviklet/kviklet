package dev.kviklet.kviklet.telemetry

import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.security.ldap.LdapUserDetailsWithId
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.authentication.event.AuthenticationSuccessEvent
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.security.saml2.provider.service.authentication.Saml2Authentication

class LoginTelemetryListenerTest {

    private val telemetry = mockk<Telemetry>(relaxed = true)
    private val listener = LoginTelemetryListener(telemetry)

    private fun fire(authentication: Authentication) =
        listener.onAuthenticationSuccess(AuthenticationSuccessEvent(authentication))

    @Test
    fun `a local password login is a password login`() {
        val user = UserDetailsWithId("user-1", "a@example.com", "secret", emptyList())
        fire(UsernamePasswordAuthenticationToken(user, "secret", emptyList()))
        verify { telemetry.track(UserLoggedIn(LoginMethod.PASSWORD), "user-1") }
    }

    @Test
    fun `an ldap login is told apart by its principal`() {
        val user = LdapUserDetailsWithId("user-2", "b@example.com", "secret", emptyList())
        fire(UsernamePasswordAuthenticationToken(user, "secret", emptyList()))
        verify { telemetry.track(UserLoggedIn(LoginMethod.LDAP), "user-2") }
    }

    @Test
    fun `an oidc login is recognised by its token and resolves the user through the principal`() {
        val details = UserDetailsWithId("user-3", "c@example.com", "secret", emptyList())
        val principal = mockk<OidcPrincipal>()
        every { principal.getUserDetails() } returns details
        every { principal.attributes } returns emptyMap()
        every { principal.authorities } returns emptyList()
        every { principal.name } returns "c@example.com"
        fire(OAuth2AuthenticationToken(principal, emptyList(), "gitlab"))
        verify { telemetry.track(UserLoggedIn(LoginMethod.OIDC), "user-3") }
    }

    @Test
    fun `a saml login is left to the saml success handler, which knows the user`() {
        val principal = mockk<Saml2AuthenticatedPrincipal>()
        every { principal.name } returns "d@example.com"
        fire(Saml2Authentication(principal, "<response/>", emptyList()))
        verify(exactly = 0) { telemetry.track(any(), any()) }
    }

    private interface OidcPrincipal :
        OAuth2User,
        KvikletOAuthPrincipal
}
