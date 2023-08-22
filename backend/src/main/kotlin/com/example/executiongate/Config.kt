package com.example.executiongate

import com.example.executiongate.service.EntityNotFound
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import jakarta.validation.ConstraintViolationException
import org.springframework.http.ResponseEntity


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

