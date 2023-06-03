package com.example.executiongate.security

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.security.Principal

@RestController
class StatusController {
    @GetMapping("/status")
    fun status(principal: Principal?): ResponseEntity<Any> {
        return if (principal != null) {
            ResponseEntity.ok().body(UserStatus(principal.name, "User is authenticated"))
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized")
        }
    }
}

data class UserStatus(val username: String, val status: String)