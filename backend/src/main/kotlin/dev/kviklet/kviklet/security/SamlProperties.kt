// This file is not MIT licensed
package dev.kviklet.kviklet.security

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "saml")
class SamlProperties {
    var enabled: Boolean = false
    var entityId: String? = null
    var ssoServiceLocation: String? = null
    var verificationCertificate: String? = null
    var userAttributes: UserAttributes = UserAttributes()

    class UserAttributes {
        var emailAttribute: String = "email"
        var nameAttribute: String = "name"
        var idAttribute: String = "nameID"
    }

    fun isSamlEnabled(): Boolean =
        enabled && entityId != null && ssoServiceLocation != null && verificationCertificate != null
}
