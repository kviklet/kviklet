package dev.kviklet.kviklet.security

interface KvikletOAuthPrincipal {
    fun getUserDetails(): UserDetailsWithId
}
