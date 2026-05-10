package dev.kviklet.kviklet.security.oauth2

import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.RoleSyncService
import jakarta.transaction.Transactional
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.core.OAuth2AuthenticationException
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

@Service
class GithubOAuth2UserService(
    private val userAuthService: UserAuthService,
    private val roleSyncService: RoleSyncService,
    private val githubProperties: GithubProperties,
    private val restTemplate: RestTemplate,
) : DefaultOAuth2UserService() {

    @Transactional
    override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
        if (userRequest.clientRegistration.registrationId != "github") {
            return super.loadUser(userRequest)
        }

        val oauth2User = super.loadUser(userRequest)
        val attributes = oauth2User.attributes

        val id = attributes["id"]?.toString()
            ?: throw OAuth2AuthenticationException(
                OAuth2Error("invalid_user_info_response", "GitHub user response missing 'id' attribute", null),
            )

        val name = attributes["name"] as? String ?: attributes["login"] as? String

        val email = attributes["email"] as? String
            ?: fetchPrimaryVerifiedEmail(userRequest.accessToken.tokenValue)

        val groups = roleSyncService.extractGroups(attributes)

        val user = userAuthService.findOrCreateUser(
            idpIdentifier = IdpIdentifier.GitHub(id),
            email = email,
            fullName = name,
            idpGroups = groups,
            requireLicense = false,
        )

        val policies = user.roles.flatMap { it.policies }.map { PolicyGrantedAuthority(it) }
        val userDetails = UserDetailsWithId(user.getId()!!, email, "", policies)

        return GithubOAuth2User(oauth2User, userDetails, policies)
    }

    private fun fetchPrimaryVerifiedEmail(accessToken: String): String {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
            set(HttpHeaders.ACCEPT, "application/vnd.github+json")
        }
        val response = try {
            restTemplate.exchange(
                githubProperties.emailsUri,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Array<GithubEmail>::class.java,
            )
        } catch (e: Exception) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "user_email_unavailable",
                    "Failed to fetch user emails from GitHub: ${e.message}",
                    null,
                ),
                e,
            )
        }

        val emails = response.body ?: emptyArray()
        val primary = emails.firstOrNull { it.primary && it.verified }
            ?: throw OAuth2AuthenticationException(
                OAuth2Error(
                    "user_email_unavailable",
                    "No primary verified email returned by GitHub. " +
                        "Make your email public on your GitHub account or grant the 'user:email' scope.",
                    null,
                ),
            )
        return primary.email
    }
}

private data class GithubEmail(
    val email: String = "",
    val primary: Boolean = false,
    val verified: Boolean = false,
    val visibility: String? = null,
)
