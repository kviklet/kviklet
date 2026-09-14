package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.service.LicenseRestrictionException
import org.springframework.security.authentication.DisabledException

const val GENERIC_LOGIN_FAILURE_MESSAGE =
    "Single sign-on failed. Please try again or contact your administrator."

/**
 * Refuses a login because the account has been deactivated. A [DisabledException] so that Spring
 * treats it as an account status problem (reported immediately, never swallowed while trying
 * further authentication providers), with a message written for the user.
 */
class AccountDeactivatedException : DisabledException(MESSAGE) {
    companion object {
        const val MESSAGE = "Your Kviklet account has been deactivated. Contact your administrator."
    }
}

/**
 * The message an SSO login failure may show on the login page. Only exceptions Kviklet raises with
 * a message written for the user are forwarded; everything else (identity provider responses,
 * Spring's own failures, bugs) collapses to a generic sentence, with the cause left to the log.
 */
fun userFacingLoginFailureMessage(exception: Throwable): String = when (exception) {
    is AccountDeactivatedException -> exception.message
    is LicenseRestrictionException -> exception.message
    else -> null
} ?: GENERIC_LOGIN_FAILURE_MESSAGE
