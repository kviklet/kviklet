package dev.kviklet.kviklet

import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement
import org.springframework.web.servlet.config.annotation.EnableWebMvc

@SpringBootApplication(
    exclude = [
        SecurityAutoConfiguration::class,
        MongoAutoConfiguration::class,
    ],
)
@EnableJpaRepositories(basePackages = ["dev.kviklet.kviklet.db"])
@EnableTransactionManagement(order = 0)
@EnableConfigurationProperties
class KvikletApplication

fun main(args: Array<String>) {
    runApplication<dev.kviklet.kviklet.KvikletApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}
