package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.LicenseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController(
    private val userAdapter: UserAdapter,
    private val licenseService: LicenseService,
    private val permissionResolver: PermissionResolver,
) {
    @GetMapping("/status")
    fun status(@CurrentUser userDetails: UserDetailsWithId): UserStatus {
        val validLicense = licenseService.getLicenses().any { it.isValid() }
        val user = userAdapter.findById(userDetails.id)
        return UserStatus(
            user.email,
            user.fullName,
            user.getId()!!,
            "User is authenticated",
            validLicense,
            permissionResolver.resolve(userDetails).toPermissionStrings(),
        )
    }
}

data class UserStatus(
    val email: String,
    val fullName: String?,
    val id: String,
    val status: String,
    val licenseValid: Boolean,
    /**
     * Permissions the user holds on at least one resource. Suitable for hiding UI a role can never
     * use; never for gating an action on a concrete connection or request, since a policy scoped
     * to one resource already counts here — use the `permissions` field on the connection /
     * execution request response for that.
     */
    val permissions: List<String>,
)
