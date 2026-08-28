// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.proxy.core.ProxyServer
import dev.kviklet.kviklet.proxy.core.TlsCertEnvConfig
import dev.kviklet.kviklet.proxy.core.tlsCertificateFactory
import dev.kviklet.kviklet.service.EventService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class ProxyServerConfig {
    // A single long-lived Postgres proxy listener, bound at application startup on the configured stable
    // port (default 5432). destroyMethod closes it on context shutdown. A port <= 0 disables the listener
    // (start() is a no-op); the test profile sets -1 so the bean never binds a real port during the Spring
    // integration tests, which spin up their own ProxyServer instances on random ports instead.
    // (The e2e stack does bind the default port, harmlessly, inside its own container network.)
    //
    // @Lazy(false) forces eager creation so the port really binds at boot: the app sets
    // spring.main.lazy-initialization=true globally, which would otherwise defer the bind until first use.
    @Bean(destroyMethod = "shutdownServer")
    @Lazy(false)
    fun postgresProxyServer(
        eventService: EventService,
        tlsCertConfig: TlsCertEnvConfig,
        @Value("\${kviklet.proxy.postgres.port:5432}") port: Int,
    ): ProxyServer {
        val server = ProxyServer(port, PostgresProtocol(eventService, tlsCertificateFactory(tlsCertConfig)))
        server.start()
        return server
    }
}
