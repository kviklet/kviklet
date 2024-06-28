package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.db.UserAdapter
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController(private val userAdapter: UserAdapter) {
    @GetMapping("/status")
    fun status(@CurrentUser userDetails: UserDetailsWithId): UserStatus {
        val user = userAdapter.findById(userDetails.id)
        return UserStatus(
            user.email,
            user.fullName,
            user.getId()!!,
            "User is authenticated",
        )
    }
}

data class UserStatus(val email: String, val fullName: String?, val id: String, val status: String)
