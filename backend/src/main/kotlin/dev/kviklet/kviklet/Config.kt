package dev.kviklet.kviklet

import com.zaxxer.hikari.HikariDataSource
import dev.kviklet.kviklet.controller.SessionWebsocketHandler
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
) {

    @Bean
    @Primary
    fun dataSource(): DataSource = HikariDataSource().apply {
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

    private fun isCertificateAuthEnabled(): Boolean =
        keyFile.isNotBlank() && certFile.isNotBlank() && rootCert.isNotBlank()
}
