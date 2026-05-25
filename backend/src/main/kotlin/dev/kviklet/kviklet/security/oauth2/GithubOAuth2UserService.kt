package dev.kviklet.kviklet.security.oauth2

import dev.kviklet.kviklet.security.IdpIdentifier
import dev.kviklet.kviklet.security.PolicyGrantedAuthority
import dev.kviklet.kviklet.security.UserAuthService
import dev.kviklet.kviklet.security.UserDetailsWithId
import dev.kviklet.kviklet.service.RoleSyncService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
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

    private val logger = LoggerFactory.getLogger(GithubOAuth2UserService::class.java)

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

        enforceAllowedOrgMembership(userRequest.accessToken.tokenValue)

        val name = attributes["name"] as? String ?: attributes["login"] as? String

        val email = fetchPrimaryVerifiedEmail(userRequest.accessToken.tokenValue)

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

    private fun enforceAllowedOrgMembership(accessToken: String) {
        val allowed = githubProperties.normalizedAllowedOrgs()
        // AuthenticationProviderValidator guarantees this is non-empty at startup.
        // Re-check here as a defence-in-depth; without it a misconfigured instance would let any
        // GitHub user in.
        if (allowed.isEmpty()) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "server_error",
                    "GitHub authentication is misconfigured: no allowed organizations set.",
                    null,
                ),
            )
        }

        val userOrgs = fetchUserOrgs(accessToken).map { it.lowercase() }.toSet()
        if (userOrgs.intersect(allowed).isEmpty()) {
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "access_denied",
                    "Your GitHub account is not a member of an organization allowed to access this Kviklet instance.",
                    null,
                ),
            )
        }
    }

    private fun fetchUserOrgs(accessToken: String): List<String> {
        val headers = HttpHeaders().apply {
            setBearerAuth(accessToken)
            set(HttpHeaders.ACCEPT, "application/vnd.github+json")
        }
        val uri = if (githubProperties.orgsUri.contains("?")) {
            "${githubProperties.orgsUri}&per_page=100"
        } else {
            "${githubProperties.orgsUri}?per_page=100"
        }
        val response = try {
            restTemplate.exchange(
                uri,
                HttpMethod.GET,
                HttpEntity<Void>(headers),
                Array<GithubOrg>::class.java,
            )
        } catch (e: Exception) {
            logger.warn("Failed to fetch user organizations from GitHub", e)
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "user_orgs_unavailable",
                    "Failed to fetch user organizations from GitHub.",
                    null,
                ),
                e,
            )
        }
        return (response.body ?: emptyArray()).map { it.login }
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
            logger.warn("Failed to fetch user emails from GitHub", e)
            throw OAuth2AuthenticationException(
                OAuth2Error(
                    "user_email_unavailable",
                    "Failed to fetch user emails from GitHub.",
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
                    "GitHub did not return a primary verified email for this account.",
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

private data class GithubOrg(val login: String = "")
