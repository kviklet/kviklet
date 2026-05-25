package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.security.ldap.LdapProperties
import dev.kviklet.kviklet.security.oauth2.GithubProperties
import dev.kviklet.kviklet.security.saml.SamlProperties
import org.springframework.beans.factory.SmartInitializingSingleton
import org.springframework.context.annotation.Configuration

@Configuration
class AuthenticationProviderValidator(
    private val ldapProperties: LdapProperties,
    private val identityProviderProperties: IdentityProviderProperties,
    private val samlProperties: SamlProperties,
    private val githubProperties: GithubProperties,
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

        if (identityProviderProperties.type?.lowercase() == "github" &&
            identityProviderProperties.isOauth2Enabled() &&
            githubProperties.normalizedAllowedOrgs().isEmpty()
        ) {
            throw IllegalStateException(
                "GitHub authentication requires kviklet.identity-provider.github.allowed-orgs " +
                    "(env: KVIKLET_IDENTITYPROVIDER_GITHUB_ALLOWEDORGS) to be set to at least one " +
                    "organization. Without this restriction any GitHub user on github.com could log " +
                    "in to this Kviklet instance.",
            )
        }
    }
}
