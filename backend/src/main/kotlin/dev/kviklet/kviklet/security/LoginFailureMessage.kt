package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.LicenseRestrictionException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.oauth2.core.OAuth2AuthenticationException

const val GENERIC_LOGIN_FAILURE_MESSAGE =
    "Single sign-on failed. Please try again or contact your administrator."

// The OAuth2 error codes Kviklet raises itself with a message written for the user (see
// GithubOAuth2UserService). Spring reports its own token and userinfo failures through the same
// exception class, so the code is what tells them apart.
private val USER_FACING_OAUTH2_ERROR_CODES = setOf(
    "access_denied",
    "user_orgs_unavailable",
    "user_email_unavailable",
)

/**
 * The message an SSO login failure may show on the login page. Only text Kviklet authored for the
 * user is forwarded; everything else (identity provider responses, Spring's own failures, bugs)
 * collapses to a generic sentence, with the actual cause left to the server log.
 */
fun userFacingLoginFailureMessage(exception: Throwable): String = when {
    exception is DisabledException -> exception.message

    exception is LicenseRestrictionException -> exception.message

    exception is OAuth2AuthenticationException && exception.error.errorCode in USER_FACING_OAUTH2_ERROR_CODES ->
        exception.error.description

    else -> null
} ?: GENERIC_LOGIN_FAILURE_MESSAGE
