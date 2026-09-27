package dev.kviklet.kviklet

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.controller.SessionWebsocketHandler
import dev.kviklet.kviklet.proxy.core.TlsCertEnvConfig
import dev.kviklet.kviklet.security.CorsSettings
import dev.kviklet.kviklet.service.AwsIamDataSource
import dev.kviklet.kviklet.service.RdsIamTokenProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Component
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry
import javax.sql.DataSource

@Component
@ConfigurationProperties("app")
class ApplicationProperties {
    lateinit var name: String
    var inDocker: Boolean = false
    var version: String = "dev"
    var buildDate: String = "unknown"
    var gitCommit: String = "unknown"
}

data class ErrorResponse(val code: Int, val type: String, val message: String, val detail: String? = null)

@Configuration
@EnableWebSocket
class WebSocketConfig : WebSocketConfigurer {
    @Autowired
    lateinit var sessionWebsocketHandler: SessionWebsocketHandler

    @Autowired
    lateinit var corsSettings: CorsSettings

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // "*" disables Spring's built-in strict origin check (which compares scheme and
        // port and breaks behind reverse proxies); WebSocketOriginInterceptor enforces
        // origin validation instead.
        registry.addHandler(sessionWebsocketHandler, "/sql/{requestId}")
            .addInterceptors(WebSocketOriginInterceptor(corsSettings.allowedOrigins), AuthHandshakeInterceptor())
            .setAllowedOriginPatterns("*")
    }
}

@Configuration
class TLSCerts(
    // The defaults must be the SpEL "#{null}", not a bare "null": a bare one injects the literal string
    // "null", which passes the factory's null-checks and ends in File("null").readLines() at boot.
    @Value("\${proxy.tls_certificate_source:NONE}")
    private val certificateSource: String,
    @Value("\${proxy.tls_certificate_cert:#{null}}")
    private val certificateCert: String?,
    @Value("\${proxy.tls_certificate_key:#{null}}")
    private val certificateKey: String?,
    @Value("\${proxy.tls_certificate_cert_file:#{null}}")
    private val certificateCertFile: String?,
    @Value("\${proxy.tls_certificate_key_file:#{null}}")
    private val certificateKeyFile: String?,
) {
    @Bean
    @Primary
    fun proxyCertificates(): TlsCertEnvConfig =
        TlsCertEnvConfig(certificateSource, certificateCertFile, certificateKeyFile, certificateKey, certificateCert)
}

@Configuration
class DataSourceConfig(
    @Value("\${spring.datasource.url}")
    private val url: String,

    @Value("\${spring.datasource.username:}")
    private val username: String,

    @Value("\${spring.datasource.password:}")
    private val password: String,

    @Value("\${spring.datasource.ssl.key-file:}")
    private val keyFile: String,

    @Value("\${spring.datasource.ssl.cert-file:}")
    private val certFile: String,

    @Value("\${spring.datasource.ssl.root-cert:}")
    private val rootCert: String,

    @Value("\${spring.datasource.driver-class-name:}")
    private val driver: String,

    @Value("\${spring.datasource.iamauth:false}")
    private val iamAuth: Boolean,
) {

    @Bean
    @Primary
    fun dataSource(rdsIamTokenProvider: RdsIamTokenProvider): DataSource {
        if (iamAuth) {
            return AwsIamDataSource(rdsIamTokenProvider, url, username).apply {
                username = this@DataSourceConfig.username
            }
        }

        return HikariDataSource().apply {
            jdbcUrl = url
            driverClassName = driver

            if (isCertificateAuthEnabled()) {
                addDataSourceProperty("ssl", "true")
                addDataSourceProperty("sslmode", "verify-full")
                addDataSourceProperty("sslkey", keyFile)
                addDataSourceProperty("sslcert", certFile)
                addDataSourceProperty("sslrootcert", rootCert)
            } else {
                this.username = this@DataSourceConfig.username
                this.password = this@DataSourceConfig.password
            }
        }
    }

    private fun isCertificateAuthEnabled(): Boolean =
        keyFile.isNotBlank() && certFile.isNotBlank() && rootCert.isNotBlank()
}
