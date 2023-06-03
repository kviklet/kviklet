package com.example.executiongate.security

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import javax.naming.AuthenticationException
import javax.servlet.http.HttpServletRequest

@RestController
class LoginController(private val customAuthenticationProvider: CustomAuthenticationProvider) {

    @PostMapping("/login")
    fun login(@RequestBody credentials: LoginCredentials, request: HttpServletRequest): ResponseEntity<Any> {
        try {
            // Create an unauthenticated token
            val authenticationToken = UsernamePasswordAuthenticationToken(credentials.username, credentials.password)

            // Attempt to authenticate the user
            val authentication = customAuthenticationProvider.authenticate(authenticationToken)

            // If successful, store the authentication instance in the SecurityContext
            SecurityContextHolder.getContext().authentication = authentication

            // Create a new session
            request.getSession(true)

            // Respond with OK status
            return ResponseEntity.ok().build()
        } catch (e: AuthenticationException) {
            // Respond with Unauthorized status
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }
    }
}

data class LoginCredentials(val username: String, val password: String)

