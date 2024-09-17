package dev.kviklet.kviklet

import dev.kviklet.kviklet.controller.SessionWebsocketHandler
import dev.kviklet.kviklet.security.CorsSettings
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

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
