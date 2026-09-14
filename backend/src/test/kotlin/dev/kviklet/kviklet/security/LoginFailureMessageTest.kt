package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.LicenseRestrictionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.InternalAuthenticationServiceException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error

class LoginFailureMessageTest {

    @Test
    fun `forwards messages Kviklet wrote for the user`() {
        assertThat(userFacingLoginFailureMessage(AccountDeactivatedException()))
            .isEqualTo(AccountDeactivatedException.MESSAGE)
        assertThat(
            userFacingLoginFailureMessage(LicenseRestrictionException("License does not allow more active users")),
        )
            .isEqualTo("License does not allow more active users")
    }

    @Test
    fun `hides everything else behind a generic message`() {
        val tokenExchange = OAuth2AuthenticationException(
            OAuth2Error("invalid_token_response", "Error retrieving the OAuth 2.0 Access Token Response: 401", null),
        )
        assertThat(userFacingLoginFailureMessage(tokenExchange)).isEqualTo(GENERIC_LOGIN_FAILURE_MESSAGE)
        assertThat(
            userFacingLoginFailureMessage(InternalAuthenticationServiceException("Connection refused: idp:8443")),
        )
            .isEqualTo(GENERIC_LOGIN_FAILURE_MESSAGE)
        assertThat(userFacingLoginFailureMessage(DisabledException("User is disabled")))
            .isEqualTo(GENERIC_LOGIN_FAILURE_MESSAGE)
        assertThat(userFacingLoginFailureMessage(IllegalStateException("No email attribute found in SAML response")))
            .isEqualTo(GENERIC_LOGIN_FAILURE_MESSAGE)
    }
}
