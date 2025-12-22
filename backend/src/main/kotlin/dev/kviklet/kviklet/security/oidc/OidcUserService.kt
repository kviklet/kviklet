package dev.kviklet.kviklet.security.oidc

import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.security.UserDetailsWithId
import jakarta.transaction.Transactional
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.stereotype.Service
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService as SpringOidcUserService
import org.springframework.security.oauth2.core.oidc.user.OidcUser as SpringOidcUser

@Service
class OidcUserService(private val userAuthService: UserAuthService) : SpringOidcUserService() {

    @Transactional
    override fun loadUser(userRequest: OidcUserRequest): SpringOidcUser {
        val oidcUser = super.loadUser(userRequest)

        val subject = oidcUser.getAttribute<String>("sub")!!
        val email = oidcUser.getAttribute<String>("email")!!
        val name = oidcUser.getAttribute<String>("name")

        val user = userAuthService.findOrCreateUser(
            idpIdentifier = IdpIdentifier.Oidc(subject),
            email = email,
            fullName = name,
            requireLicense = false,
        )

        val policies = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }
        val userDetails = UserDetailsWithId(user.getId()!!, email, "", policies)

        return OidcUser(oidcUser, userDetails, policies)
    }
}
