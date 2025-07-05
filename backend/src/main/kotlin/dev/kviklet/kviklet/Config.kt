package dev.kviklet.kviklet

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.controller.SessionWebsocketHandler
import dev.kviklet.kviklet.proxy.postgres.TlsCertEnvConfig
import dev.kviklet.kviklet.security.CorsSettings
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
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.rds.RdsUtilities
import java.net.URI
import javax.sql.DataSource

@Component
@ConfigurationProperties("app")
class MyProperties {
    lateinit var name: String
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
        val handlerRegistry = registry.addHandler(sessionWebsocketHandler, "/sql/{requestId}")
            .addInterceptors(AuthHandshakeInterceptor())
        if (corsSettings.allowedOrigins.isNotEmpty()) {
            handlerRegistry.setAllowedOrigins(*corsSettings.allowedOrigins.toTypedArray())
        }
    }
}

@Configuration
class TLSCerts(
    @Value("\${proxy.tls_certificate_source:NONE}")
    private val certificateSource: String,
    @Value("\${proxy.tls_certificate_cert:null}")
    private val certificateCert: String,
    @Value("\${proxy.tls_certificate_key:null}")
    private val certificateKey: String,
    @Value("\${proxy.tls_certificate_cert_file:null}")
    private val certificateCertFile: String,
    @Value("\${proxy.tls_certificate_key_file:null}")
    private val certificateKeyFile: String,
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
    fun dataSource(): DataSource {
        if (iamAuth) {
            val dataSource = KvikletAwsIamDataSource().apply {
                jdbcUrl = url
                maxLifetime = 840000
                username = this@DataSourceConfig.username
                initialize()
            }
            return dataSource
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

class KvikletAwsIamDataSource : HikariDataSource() {
    private lateinit var rdsUtilities: RdsUtilities
    private lateinit var uri: URI
    private lateinit var region: Region

    fun initialize() {
        uri = URI.create(jdbcUrl.removePrefix("jdbc:"))
        region = extractRegionFromHost(uri.host)

        rdsUtilities = RdsUtilities.builder()
            .region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }

    private fun extractRegionFromHost(host: String): Region {
        // Expected format: <db-instance>.<region>.rds.amazonaws.com
        val parts = host.split(".")
        if (parts.size < 5 ||
            parts[parts.size - 3] != "rds" ||
            parts[parts.size - 2] != "amazonaws" ||
            parts[parts.size - 1] != "com"
        ) {
            throw IllegalArgumentException(
                "Invalid RDS endpoint format. Expected: <db-instance>.<region>.rds.amazonaws.com",
            )
        }

        // The region is the second-to-last segment before "rds.amazonaws.com"
        val regionString = parts[parts.size - 4]

        return try {
            Region.of(regionString)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid AWS region: $regionString", e)
        }
    }

    override fun getPassword(): String {
        val token = rdsUtilities.generateAuthenticationToken { builder ->
            builder.hostname(uri.host)
                .port(uri.port)
                .username(username)
        }
        return token
    }
}
