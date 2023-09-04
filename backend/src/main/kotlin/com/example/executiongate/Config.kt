package com.example.executiongate

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties("app")
class MyProperties {
    lateinit var name: String
}

data class ErrorResponse(
    val code: Int,
    val type: String,
    val message: String,
    val detail: String? = null,
)
