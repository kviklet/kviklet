// This file is not MIT licensed
package dev.kviklet.kviklet.security.saml

import dev.kviklet.kviklet.ApplicationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.saml2.core.Saml2X509Credential
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository
import org.springframework.security.saml2.provider.service.registration.Saml2MessageBinding
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

@Configuration
@ConditionalOnProperty(prefix = "saml", name = ["enabled"], havingValue = "true")
class SamlConfig(
    private val samlProperties: SamlProperties,
    private val applicationProperties: ApplicationProperties,
) {

    @Bean
    fun relyingPartyRegistrationRepository(): RelyingPartyRegistrationRepository {
        if (!samlProperties.isSamlEnabled()) {
            throw IllegalStateException("SAML is enabled but required properties are missing")
        }

        val certificateString = samlProperties.verificationCertificate!!
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(certificateString.byteInputStream()) as X509Certificate

        val verificationCredential = Saml2X509Credential.verification(certificate)

        // Conditionally set the SAML URLs based on deployment environment
        val assertionConsumerServiceLocation = if (applicationProperties.inDocker) {
            // When running in Docker behind nginx reverse proxy, include /api prefix
            "{baseUrl}/api/login/saml2/sso/{registrationId}"
        } else {
            // Default location for direct access
            "{baseUrl}/login/saml2/sso/{registrationId}"
        }

        val entityId = if (applicationProperties.inDocker) {
            "{baseUrl}/api/saml2/service-provider-metadata/{registrationId}"
        } else {
            "{baseUrl}/saml2/service-provider-metadata/{registrationId}"
        }

        val singleLogoutServiceLocation = if (applicationProperties.inDocker) {
            "{baseUrl}/api/logout/saml2/slo"
        } else {
            "{baseUrl}/logout/saml2/slo"
        }

        val registration = RelyingPartyRegistration
            .withRegistrationId("saml")
            .entityId(entityId)
            .assertingPartyMetadata { party ->
                party
                    .entityId(samlProperties.entityId!!)
                    .singleSignOnServiceLocation(samlProperties.ssoServiceLocation!!)
                    .wantAuthnRequestsSigned(false)
                    .verificationX509Credentials { c -> c.add(verificationCredential) }
            }
            .assertionConsumerServiceLocation(assertionConsumerServiceLocation)
            .assertionConsumerServiceBinding(Saml2MessageBinding.REDIRECT)
            .singleLogoutServiceLocation(singleLogoutServiceLocation)
            .singleLogoutServiceBinding(Saml2MessageBinding.REDIRECT)
            .build()

        return InMemoryRelyingPartyRegistrationRepository(registration)
    }
}
