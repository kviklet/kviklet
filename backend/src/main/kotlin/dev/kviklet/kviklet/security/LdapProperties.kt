package dev.kviklet.kviklet.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.ldap.core.LdapTemplate
import org.springframework.ldap.core.support.LdapContextSource

@ConfigurationProperties(prefix = "ldap")
@Configuration
class LdapProperties {
    var userOu: String = "people"
    var searchBase: String? = null
    var uniqueIdentifierAttribute: String = "uid"
    var emailAttribute: String = "mail"
    var fullNameAttribute: String = "cn"
    var base: String = "dc=kviklet,dc=dev"
    var url: String = "ldap://localhost:389"
    var enabled: Boolean = false
    var principal = "cn=admin,dc=kviklet,dc=dev"
    var password = "admin"

    @Bean
    fun ldapTemplate(contextSource: LdapContextSource): LdapTemplate = LdapTemplate(contextSource)

    @Bean
    fun contextSource(): LdapContextSource {
        val contextSource = LdapContextSource()
        contextSource.setUrl(url)
        contextSource.setBase(base)
        contextSource.userDn = principal
        contextSource.password = password
        contextSource.afterPropertiesSet()

        return contextSource
    }
}
