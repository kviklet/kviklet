package com.example.executiongate.security

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken

class MyToken(
    userDetails: UserDetailsWithId,
    accessToken: OAuth2AccessToken,
    credentials: Any,
    authorities: Collection<GrantedAuthority>,
) : AbstractOAuth2TokenAuthenticationToken<OAuth2AccessToken>(
    accessToken,
    userDetails,
    credentials,
    authorities,
) {
    init {
        isAuthenticated = true
    }
    override fun getTokenAttributes(): MutableMap<String, Any> = mutableMapOf()
}
