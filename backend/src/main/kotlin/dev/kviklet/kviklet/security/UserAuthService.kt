package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.User
import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.LicenseRestrictionException
import dev.kviklet.kviklet.service.LicenseService
import dev.kviklet.kviklet.service.RoleSyncService
import dev.kviklet.kviklet.service.dto.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Represents an identity provider-specific user identifier.
 */
sealed class IdpIdentifier {
    data class Saml(val nameId: String) : IdpIdentifier()
    data class Oidc(val subject: String) : IdpIdentifier()
    data class Ldap(val identifier: String) : IdpIdentifier()
}

/**
 * Centralized service for user authentication across all identity providers.
 * Handles user lookup, creation, and migration between auth methods.
 */
@Service
class UserAuthService(
    private val userAdapter: UserAdapter,
    private val roleAdapter: RoleAdapter,
    private val licenseService: LicenseService,
    private val roleSyncService: RoleSyncService,
) {
    /**
     * Find existing user or create new one during authentication.
     * Handles migration between auth methods (e.g., password → OIDC).
     *
     * @param idpIdentifier The identity provider-specific identifier
     * @param email User's email from the identity provider
     * @param fullName User's display name from the identity provider
     * @param idpGroups List of group names from the identity provider for role sync
     * @param requireLicense If true, throws if no valid license (for SAML)
     * @return The user (existing or newly created)
     */
    @Transactional
    fun findOrCreateUser(
        idpIdentifier: IdpIdentifier,
        email: String,
        fullName: String?,
        idpGroups: List<String> = emptyList(),
        requireLicense: Boolean = false,
    ): User {
        // 1. Check license upfront if required (SAML only)
        val license = licenseService.getActiveLicense()
        if (requireLicense) {
            if (license == null || !license.isValid()) {
                throw LicenseRestrictionException("SAML authentication requires a valid license")
            }
        }

        // 2. Find by IdP-specific identifier
        var user = findByIdpIdentifier(idpIdentifier)
        var isNewUser = false

        if (user == null) {
            // 3. Try to find by email (migration case)
            user = userAdapter.findByEmail(email)

            if (user == null) {
                // 4. Create new user - check license user limit
                if (license != null) {
                    val maxUsers = license.allowedUsers
                    if (maxUsers <= userAdapter.listUsers().size.toUInt()) {
                        throw LicenseRestrictionException("License does not allow more users")
                    }
                }

                val defaultRole = roleAdapter.findById(Role.DEFAULT_ROLE_ID)
                user = User(
                    email = email,
                    fullName = fullName ?: email,
                    roles = setOf(defaultRole),
                )
                isNewUser = true
            }
        }

        // 5. Update user with current IdP identifier, clear others consistently
        user = updateUserIdentifier(user, idpIdentifier, email, fullName)

        // 6. Role sync - resolve roles based on IdP groups
        val resolvedRoles = roleSyncService.resolveRoles(idpGroups, user.roles, isNewUser)
        user = user.copy(roles = resolvedRoles)

        return userAdapter.createOrUpdateUser(user)
    }

    private fun findByIdpIdentifier(idpIdentifier: IdpIdentifier): User? = when (idpIdentifier) {
        is IdpIdentifier.Saml -> userAdapter.findBySamlNameId(idpIdentifier.nameId)
        is IdpIdentifier.Oidc -> userAdapter.findBySubject(idpIdentifier.subject)
        is IdpIdentifier.Ldap -> userAdapter.findByLdapIdentifier(idpIdentifier.identifier)
    }

    private fun updateUserIdentifier(
        user: User,
        idpIdentifier: IdpIdentifier,
        email: String,
        fullName: String?,
    ): User {
        // Clear ALL other auth identifiers consistently when switching auth methods
        return when (idpIdentifier) {
            is IdpIdentifier.Saml -> user.copy(
                samlNameId = idpIdentifier.nameId,
                subject = null,
                ldapIdentifier = null,
                password = null,
                email = email,
                fullName = fullName ?: user.fullName,
            )

            is IdpIdentifier.Oidc -> user.copy(
                subject = idpIdentifier.subject,
                samlNameId = null,
                ldapIdentifier = null,
                password = null,
                email = email,
                fullName = fullName ?: user.fullName,
            )

            is IdpIdentifier.Ldap -> user.copy(
                ldapIdentifier = idpIdentifier.identifier,
                subject = null,
                samlNameId = null,
                password = null,
                email = email,
                fullName = fullName ?: user.fullName,
            )
        }
    }
}
