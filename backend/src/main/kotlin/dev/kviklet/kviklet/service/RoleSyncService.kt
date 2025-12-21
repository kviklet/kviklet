package dev.kviklet.kviklet.service

import dev.kviklet.kviklet.db.RoleAdapter
import dev.kviklet.kviklet.db.RoleSyncConfigAdapter
import dev.kviklet.kviklet.security.NoPolicy
import dev.kviklet.kviklet.service.dto.Role
import dev.kviklet.kviklet.service.dto.RoleId
import dev.kviklet.kviklet.service.dto.SyncMode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RoleSyncService(private val roleSyncConfigAdapter: RoleSyncConfigAdapter, private val roleAdapter: RoleAdapter) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Resolve roles for a user based on their IdP groups.
     *
     * @param idpGroups List of group names from the identity provider
     * @param existingRoles User's current roles
     * @param isNewUser Whether this is the user's first login
     * @return Set of roles to assign to the user
     */
    @NoPolicy
    fun resolveRoles(idpGroups: List<String>, existingRoles: Set<Role>, isNewUser: Boolean): Set<Role> {
        val config = roleSyncConfigAdapter.getConfig()
        logger.info(
            "resolveRoles called: idpGroups=$idpGroups, isNewUser=$isNewUser, config.enabled=${config.enabled}, syncMode=${config.syncMode}",
        )

        // If sync disabled, return default for new users, existing for others
        if (!config.enabled) {
            logger.info("Role sync is disabled, returning default behavior")
            return if (isNewUser) {
                setOf(roleAdapter.findById(Role.DEFAULT_ROLE_ID))
            } else {
                existingRoles
            }
        }

        // FIRST_LOGIN_ONLY: don't change existing users
        if (!isNewUser && config.syncMode == SyncMode.FIRST_LOGIN_ONLY) {
            return existingRoles
        }

        // Map IdP groups to Kviklet roles
        val mappings = roleSyncConfigAdapter.getMappingsByGroupNames(idpGroups)
        logger.info("Found ${mappings.size} mappings for groups $idpGroups: $mappings")
        val mappedRoles = mappings
            .mapNotNull { mapping ->
                try {
                    roleAdapter.findById(RoleId(mapping.roleId))
                } catch (e: EntityNotFound) {
                    null // Role was deleted, skip this mapping
                }
            }
            .toSet()

        // If no groups matched, give default role for new users, keep existing for others
        if (mappedRoles.isEmpty()) {
            return if (isNewUser) {
                setOf(roleAdapter.findById(Role.DEFAULT_ROLE_ID))
            } else {
                existingRoles
            }
        }

        // Always include the default role
        val defaultRole = roleAdapter.findById(Role.DEFAULT_ROLE_ID)

        return when (config.syncMode) {
            SyncMode.FULL_SYNC -> mappedRoles + defaultRole
            SyncMode.ADDITIVE -> existingRoles + mappedRoles + defaultRole
            SyncMode.FIRST_LOGIN_ONLY -> mappedRoles + defaultRole // Already handled above for existing users
        }
    }

    /**
     * Extract groups from principal attributes (SAML/OIDC).
     * Uses the configured groups attribute name.
     */
    @NoPolicy
    fun extractGroups(attributes: Map<String, Any?>): List<String> {
        val config = roleSyncConfigAdapter.getConfig()
        logger.info("Looking for groups attribute '${config.groupsAttribute}' in attributes: ${attributes.keys}")
        val groupsAttr = attributes[config.groupsAttribute]
        logger.info("Found groups attribute value: $groupsAttr (type: ${groupsAttr?.javaClass?.name})")

        if (groupsAttr == null) {
            return emptyList()
        }

        val groups = when (groupsAttr) {
            is List<*> -> groupsAttr.filterIsInstance<String>()
            is String -> listOf(groupsAttr)
            else -> emptyList()
        }
        logger.info("Extracted groups: $groups")
        return groups
    }

    /**
     * Extract groups from LDAP memberOf attribute (DNs).
     * Parses "cn=GroupName,ou=groups,dc=example,dc=com" → "GroupName"
     */
    @NoPolicy
    fun extractGroupsFromLdapDn(memberOfValues: List<String>): List<String> = memberOfValues.mapNotNull { dn ->
        dn.split(",")
            .firstOrNull { it.lowercase().startsWith("cn=") }
            ?.substringAfter("=")
    }
}
