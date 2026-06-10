package dev.kviklet.kviklet.security.oauth2

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "kviklet.identity-provider.github")
class GithubProperties {
    var authorizationUri: String = "https://github.com/login/oauth/authorize"
    var tokenUri: String = "https://github.com/login/oauth/access_token"
    var userInfoUri: String = "https://api.github.com/user"
    var emailsUri: String = "https://api.github.com/user/emails"
    var orgsUri: String = "https://api.github.com/user/orgs"
    var allowedOrgs: List<String> = emptyList()

    fun normalizedAllowedOrgs(): Set<String> = allowedOrgs.map {
        it.trim().lowercase()
    }.filter { it.isNotBlank() }.toSet()
}
