package dev.kviklet.kviklet.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class ApiConfig {
    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
