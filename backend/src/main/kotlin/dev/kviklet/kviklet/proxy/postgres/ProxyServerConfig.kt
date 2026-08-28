// This file is not MIT licensed
package dev.kviklet.kviklet.proxy.postgres

import dev.kviklet.kviklet.service.EventService
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

@Configuration
class ProxyServerConfig {
    // A single long-lived Postgres proxy listener, bound at application startup on the configured stable
    // port (default 5432). destroyMethod closes it on context shutdown. A port <= 0 disables the listener
    // (start() is a no-op); the test and e2e profiles set -1 so the bean never binds a real port there, and
    // the proxy tests spin up their own PostgresProxyServer instances on random ports instead.
    //
    // @Lazy(false) forces eager creation so the port really binds at boot: the app sets
    // spring.main.lazy-initialization=true globally, which would otherwise defer the bind until first use.
    @Bean(destroyMethod = "shutdownServer")
    @Lazy(false)
    fun postgresProxyServer(
        eventService: EventService,
        tlsCertConfig: TlsCertEnvConfig,
        @Value("\${kviklet.proxy.postgres.port:5432}") port: Int,
    ): PostgresProxyServer {
        val server = PostgresProxyServer(port, eventService, tlsCertificateFactory(tlsCertConfig))
        server.start()
        return server
    }
}
