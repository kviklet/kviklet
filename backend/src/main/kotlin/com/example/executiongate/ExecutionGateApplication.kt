package com.example.executiongate

import org.springframework.boot.Banner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SpringBootApplication(exclude = [ SecurityAutoConfiguration::class ])
@EnableJpaRepositories(basePackages = ["com.example.executiongate.db"])
class ExecutionGateApplication

fun main(args: Array<String>) {
    runApplication<ExecutionGateApplication>(*args) {
        setBannerMode(Banner.Mode.OFF)
    }
}
