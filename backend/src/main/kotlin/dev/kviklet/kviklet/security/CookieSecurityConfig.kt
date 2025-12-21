package dev.kviklet.kviklet.security

import dev.kviklet.kviklet.security.saml.SamlProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.session.web.http.CookieSerializer
import org.springframework.session.web.http.DefaultCookieSerializer

@Configuration
class CookieSecurityConfig(private val samlProperties: SamlProperties) {

    @Bean
    fun cookieSerializer(): CookieSerializer {
        val serializer = DefaultCookieSerializer()

        if (samlProperties.isSamlEnabled()) {
            // SAML requires cross-origin cookies, which mandates secure=true and same-site=none
            serializer.setSameSite("None")
            serializer.setUseSecureCookie(true)
        } else {
            // Default: standard security settings that work with HTTP
            serializer.setSameSite("Lax")
            serializer.setUseSecureCookie(false)
        }

        return serializer
    }
}
