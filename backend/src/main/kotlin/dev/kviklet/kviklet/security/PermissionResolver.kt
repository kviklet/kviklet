package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * Single source of truth for "may this user do X (to this object)?".
 *
 * [MyAuthorizationManager] uses it to enforce `@Policy`, and the read paths use it to tell the
 * frontend which permissions a user holds, so the UI answer can never drift from the enforcement
 * answer. Everything it reports is the policy vote plus the [SecuredDomainObject.auth] hook —
 * service-body rules (a user not reviewing their own request, dry-run-only execution, execution
 * count limits) are deliberately not modelled here.
 */
@Component
class PermissionResolver(private val userAdapter: UserAdapter) {

    fun policiesFor(userId: String): List<PolicyGrantedAuthority> = userAdapter.findById(userId).roles
        .map { role -> role.policies.map { PolicyGrantedAuthority(it) } }
        .flatten()

    /**
     * Walks the [Permission.requiredPermission] chain, voting the user's policies at every step and
     * consulting the object's own [SecuredDomainObject.auth] hook.
     *
     * Passing `obj = null` asks the unscoped question: "is this action allowed on at least one
     * resource?" A policy scoped to a specific resource still answers yes to that (see
     * [PolicyGrantedAuthority.vote]), so a `null` answer must never be used to gate an action on a
     * concrete object.
     */
    fun isAllowed(
        permission: Permission,
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
        obj: SecuredDomainObject? = null,
    ): Boolean {
        var permissionToCheck = permission
        var securedObject = obj

        do {
            if (!policies.vote(permissionToCheck, securedObject)) {
                return false
            }
            if (obj?.auth(permissionToCheck, userDetails, policies) == false) {
                return false
            }
        } while ((permissionToCheck.requiredPermission != null).also {
                if (it) {
                    permissionToCheck = permissionToCheck.requiredPermission!!
                    securedObject = securedObject?.getRelated(permissionToCheck.resource)
                }
            }
        )
        return true
    }

    /**
     * Every permission the user holds, either globally (`obj = null`) or on [obj]. The result is
     * transitively closed, because [isAllowed] walks the `requiredPermission` chain.
     */
    fun resolve(
        userDetails: UserDetailsWithId,
        policies: List<PolicyGrantedAuthority>,
        obj: SecuredDomainObject? = null,
    ): Set<Permission> = candidatesFor(obj).filter { isAllowed(it, userDetails, policies, obj) }.toSet()

    /**
     * Which permissions it is meaningful to ask about for [obj]. Connections and execution requests
     * are both scoped by connection id and relate to each other — "may I edit this connection?" and
     * "may I open a request against it?" are both fair questions about a connection — so either
     * object answers for both families. Asking a connection about, say, `configuration:edit` is
     * meaningless, and [SecuredDomainObject.getRelated] rejects the unrelated resource outright.
     */
    private fun candidatesFor(obj: SecuredDomainObject?): List<Permission> {
        if (obj == null) return Permission.entries
        val connectionScoped = setOf(Resource.DATASOURCE_CONNECTION, Resource.EXECUTION_REQUEST)
        val families = if (obj.getDomainObjectType() in connectionScoped) {
            connectionScoped
        } else {
            setOf(obj.getDomainObjectType())
        }
        return Permission.entries.filter { it.resource in families }
    }

    fun resolve(userDetails: UserDetailsWithId, obj: SecuredDomainObject? = null): Set<Permission> =
        resolve(userDetails, policiesFor(userDetails.id), obj)

    /**
     * Convenience for service read paths that stamp permissions onto a DTO. Calls made without a
     * security context (internal calls, tests) resolve to no permissions rather than to everything,
     * so an absent context can only ever under-report.
     */
    fun resolveForCurrentUser(obj: SecuredDomainObject? = null): Set<Permission> {
        val principal = SecurityContextHolder.getContextHolderStrategy().context.authentication?.principal
        val userDetails = principal as? UserDetailsWithId ?: return emptySet()
        return resolve(userDetails, obj)
    }
}

fun Set<Permission>.toPermissionStrings(): List<String> = this.map { it.getPermissionString() }.sorted()
