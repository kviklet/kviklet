package dev.kviklet.kviklet.security.oidc

import dev.kviklet.kviklet.security.UserDetailsWithId
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.oidc.OidcIdToken
import org.springframework.security.oauth2.core.oidc.OidcUserInfo
import java.io.Serializable
import org.springframework.security.oauth2.core.oidc.user.OidcUser as SpringOidcUser

class OidcUser(
    private val oidcUser: SpringOidcUser,
    private val userDetails: UserDetailsWithId,
    private val authorities: Collection<GrantedAuthority>,
) : SpringOidcUser,
    Serializable {

    override fun getClaims(): Map<String, Any> = oidcUser.claims

    override fun getIdToken(): OidcIdToken = oidcUser.idToken

    override fun getUserInfo(): OidcUserInfo = oidcUser.userInfo

    override fun getName(): String = oidcUser.name

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getAttributes(): Map<String, Any> = oidcUser.attributes

    fun getUserDetails(): UserDetailsWithId = userDetails

    companion object {
        private const val serialVersionUID = 1L
    }
}
