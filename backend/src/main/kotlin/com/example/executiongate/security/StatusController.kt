package com.example.executiongate.security

import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class StatusController {
    @GetMapping("/status")
    fun status(@AuthenticationPrincipal userDetails: UserDetailsWithId): ResponseEntity<Any> {
        return ResponseEntity.ok().body(UserStatus(userDetails.username, userDetails.id, "User is authenticated"))
    }
}

data class UserStatus(val email: String, val id: String, val status: String)