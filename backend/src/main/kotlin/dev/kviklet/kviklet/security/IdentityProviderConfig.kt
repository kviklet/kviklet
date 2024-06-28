package dev.kviklet.kviklet.security

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService
import org.springframework.security.oauth2.client.endpoint.DefaultAuthorizationCodeTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.AuthorizationGrantType
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.stereotype.Component

@Configuration
@ConfigurationProperties(prefix = "kviklet.identity-provider")
class IdentityProviderProperties {
    var type: String? = null
    var clientId: String? = null
    var clientSecret: String? = null
    var issuerUri: String? = null

    fun isOauth2Enabled(): Boolean = type != null && clientId != null && clientSecret != null

    fun getIssuer(): String = if (type == "google") {
        "https://accounts.google.com"
    } else {
        issuerUri!!
    }
}

@Component
@ConditionalOnProperty(name = ["kviklet.identity-provider.type"])
@EnableWebSecurity
data class IdentityProviderConfig(
    private val properties: IdentityProviderProperties,
    private val environment: Environment,
) {

    @Order(2)
    @Bean
    fun clientRegistrationRepository(): ClientRegistrationRepository {
        val activeProfiles = environment.activeProfiles
        val redirectUri = if (!activeProfiles.contains("test") &&
            !activeProfiles.contains("local") &&
            !activeProfiles.contains("e2e")
        ) {
            "{baseUrl}/api/login/oauth2/code/{registrationId}"
        } else {
            "{baseUrl}/login/oauth2/code/{registrationId}"
        }
        val clientRegistration = ClientRegistrations
            .fromIssuerLocation(properties.getIssuer())
            .registrationId(properties.type)
            .clientId(properties.clientId)
            .clientSecret(properties.clientSecret)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(redirectUri)
            .scope("openid", "email", "profile")
            .userNameAttributeName(IdTokenClaimNames.SUB)
            .clientName(properties.type!!.replaceFirstChar { it.uppercase() })
            .build()

        return InMemoryClientRegistrationRepository(clientRegistration)
    }

    @Bean
    fun authorizedClientService(
        clientRegistrationRepository: ClientRegistrationRepository,
    ): OAuth2AuthorizedClientService = InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)

    @Bean
    fun accessTokenResponseClient(): OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> =
        DefaultAuthorizationCodeTokenResponseClient()

    @Bean
    fun oauth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> = DefaultOAuth2UserService()
}
