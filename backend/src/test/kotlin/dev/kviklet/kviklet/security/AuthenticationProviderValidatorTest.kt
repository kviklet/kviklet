package dev.kviklet.kviklet.security

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class AuthenticationProviderValidatorTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                LdapAutoConfiguration::class.java,
            ),
        )
        .withUserConfiguration(
            LdapProperties::class.java,
            IdentityProviderProperties::class.java,
            SamlProperties::class.java,
            AuthenticationProviderValidator::class.java,
        )
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:testdb",
            "spring.datasource.driver-class-name=org.h2.Driver",
        )

    @Test
    fun `context loads when no external auth providers enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=false",
                "saml.enabled=false",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(AuthenticationProviderValidator::class.java)
            }
    }

    @Test
    fun `context loads when only LDAP enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=true",
                "ldap.url=ldap://localhost:389",
                "ldap.base=dc=example,dc=org",
                "saml.enabled=false",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
            }
    }

    @Test
    fun `context loads when only OIDC enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=false",
                "saml.enabled=false",
                "kviklet.identity-provider.type=oidc",
                "kviklet.identity-provider.client-id=test-client",
                "kviklet.identity-provider.client-secret=test-secret",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
            }
    }

    @Test
    fun `context loads when only SAML enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=false",
                "saml.enabled=true",
                "saml.entityId=https://example.com/saml",
                "saml.ssoServiceLocation=https://idp.example.com/sso",
                "saml.verificationCertificate=-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----",
            )
            .run { context ->
                assertThat(context).hasNotFailed()
            }
    }

    @Test
    fun `context fails when LDAP and OIDC both enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=true",
                "ldap.url=ldap://localhost:389",
                "ldap.base=dc=example,dc=org",
                "saml.enabled=false",
                "kviklet.identity-provider.type=oidc",
                "kviklet.identity-provider.client-id=test-client",
                "kviklet.identity-provider.client-secret=test-secret",
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .rootCause()
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Only one external authentication provider can be enabled at a time")
                    .hasMessageContaining("LDAP")
                    .hasMessageContaining("OAuth2/OIDC")
            }
    }

    @Test
    fun `context fails when LDAP and SAML both enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=true",
                "ldap.url=ldap://localhost:389",
                "ldap.base=dc=example,dc=org",
                "saml.enabled=true",
                "saml.entityId=https://example.com/saml",
                "saml.ssoServiceLocation=https://idp.example.com/sso",
                "saml.verificationCertificate=-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----",
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .rootCause()
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Only one external authentication provider can be enabled at a time")
                    .hasMessageContaining("LDAP")
                    .hasMessageContaining("SAML")
            }
    }

    @Test
    fun `context fails when OIDC and SAML both enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=false",
                "kviklet.identity-provider.type=oidc",
                "kviklet.identity-provider.client-id=test-client",
                "kviklet.identity-provider.client-secret=test-secret",
                "saml.enabled=true",
                "saml.entityId=https://example.com/saml",
                "saml.ssoServiceLocation=https://idp.example.com/sso",
                "saml.verificationCertificate=-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----",
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .rootCause()
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Only one external authentication provider can be enabled at a time")
                    .hasMessageContaining("OAuth2/OIDC")
                    .hasMessageContaining("SAML")
            }
    }

    @Test
    fun `context fails when all three providers enabled`() {
        contextRunner
            .withPropertyValues(
                "ldap.enabled=true",
                "ldap.url=ldap://localhost:389",
                "ldap.base=dc=example,dc=org",
                "kviklet.identity-provider.type=oidc",
                "kviklet.identity-provider.client-id=test-client",
                "kviklet.identity-provider.client-secret=test-secret",
                "saml.enabled=true",
                "saml.entityId=https://example.com/saml",
                "saml.ssoServiceLocation=https://idp.example.com/sso",
                "saml.verificationCertificate=-----BEGIN CERTIFICATE-----\ntest\n-----END CERTIFICATE-----",
            )
            .run { context ->
                assertThat(context).hasFailed()
                assertThat(context.startupFailure)
                    .rootCause()
                    .isInstanceOf(IllegalStateException::class.java)
                    .hasMessageContaining("Only one external authentication provider can be enabled at a time")
                    .hasMessageContaining("LDAP")
                    .hasMessageContaining("OAuth2/OIDC")
                    .hasMessageContaining("SAML")
            }
    }
}
