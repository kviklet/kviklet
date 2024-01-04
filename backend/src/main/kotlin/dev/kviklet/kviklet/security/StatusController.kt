package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import dev.kviklet.kviklet.service.LicenseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController(
    private val userAdapter: UserAdapter,
    private val licenseService: LicenseService,
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
        )
    }
}

data class UserStatus(
    val email: String,
    val fullName: String?,
    val id: String,
    val status: String,
    val licenseValid: Boolean,
)
