package dev.kviklet.kviklet.security

import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.Configuration

@Configuration
class AuthenticationProviderValidator(
    private val ldapProperties: LdapProperties,
    private val identityProviderProperties: IdentityProviderProperties,
    private val samlProperties: SamlProperties,
) : SmartInitializingSingleton {
    override fun afterSingletonsInstantiated() {
        val enabledProviders = mutableListOf<String>()

        if (ldapProperties.enabled) {
            enabledProviders.add("LDAP")
        }
        if (identityProviderProperties.isOauth2Enabled()) {
            enabledProviders.add("OAuth2/OIDC")
        }
        if (samlProperties.isSamlEnabled()) {
            enabledProviders.add("SAML")
        }

        if (enabledProviders.size > 1) {
            throw IllegalStateException(
                "Only one external authentication provider can be enabled at a time. " +
                    "Currently enabled: ${enabledProviders.joinToString(", ")}. " +
                    "Please disable all but one provider.",
            )
        }
    }
}
