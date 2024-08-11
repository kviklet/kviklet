package dev.kviklet.kviklet.db.init

import dev.kviklet.kviklet.db.ConnectionAdapter
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@Profile("!test")
class EncryptionInitializer {
    @Bean
    fun rotateAndEncrypt(connectionAdapter: ConnectionAdapter): ApplicationRunner = ApplicationRunner { _ ->
        // listing all connections ensures they get encrypted if not already done
        // Also rotates they encryption key if two are provided
        connectionAdapter.listConnections()
    }
}
