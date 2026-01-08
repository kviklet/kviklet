// This file is not MIT licensed
package dev.kviklet.kviklet.security.saml

import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.service.RoleSyncService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(prefix = "saml", name = ["enabled"], havingValue = "true")
class SamlUserService(
    private val samlProperties: SamlProperties,
    private val userAuthService: UserAuthService,
    private val roleSyncService: RoleSyncService,
) {
    @Transactional
    fun loadUser(principal: Saml2AuthenticatedPrincipal): dev.kviklet.kviklet.db.User {
        val nameId = principal.name

        val email = principal.getAttribute<String>(samlProperties.userAttributes.emailAttribute)?.firstOrNull()
            ?: throw IllegalStateException(
                "No email attribute found in SAML response. Available attributes: ${principal.attributes.keys}",
            )
        val fullName = principal.getAttribute<String>(samlProperties.userAttributes.nameAttribute)?.firstOrNull()

        // Extract groups from SAML attributes for role sync
        val groups = roleSyncService.extractGroups(principal.attributes)

        return userAuthService.findOrCreateUser(
            idpIdentifier = IdpIdentifier.Saml(nameId),
            email = email,
            fullName = fullName,
            idpGroups = groups,
            requireLicense = true,
        )
    }
}
