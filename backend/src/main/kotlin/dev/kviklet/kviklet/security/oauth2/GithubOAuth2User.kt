package dev.kviklet.kviklet.security.oauth2

import dev.kviklet.kviklet.security.KvikletOAuthPrincipal
import dev.kviklet.kviklet.security.UserDetailsWithId
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.user.OAuth2User
import java.io.Serializable

class GithubOAuth2User(
    private val delegate: OAuth2User,
    private val userDetails: UserDetailsWithId,
    private val authorities: Collection<GrantedAuthority>,
) : OAuth2User,
    KvikletOAuthPrincipal,
    Serializable {

    override fun getName(): String = delegate.name

    override fun getAttributes(): Map<String, Any> = delegate.attributes

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun getUserDetails(): UserDetailsWithId = userDetails

    companion object {
        private const val serialVersionUID = 1L
    }
}
