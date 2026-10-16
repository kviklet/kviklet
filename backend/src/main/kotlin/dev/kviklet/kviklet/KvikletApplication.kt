package dev.kviklet.kviklet

import org.springframework.ai.mcp.server.common.autoconfigure.McpServerStatelessAutoConfiguration
import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

// The MCP server is built in McpServerConfig instead, to filter the tools each user sees.
@SpringBootApplication(exclude = [SecurityAutoConfiguration::class, McpServerStatelessAutoConfiguration::class])
@EnableJpaRepositories(basePackages = ["dev.kviklet.kviklet.db"])
@EnableTransactionManagement(order = 0)
@EnableConfigurationProperties
class KvikletApplication

fun main(args: Array<String>) {
    runApplication<dev.kviklet.kviklet.KvikletApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}
