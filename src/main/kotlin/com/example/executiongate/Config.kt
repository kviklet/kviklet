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
import javax.validation.ConstraintViolationException


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


@RestControllerAdvice
@Order(1)
class RestControllerExceptionHandler {
    @RequestMapping(value = ["error/404"], method = [RequestMethod.GET])
    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(exception: Exception?) = ErrorResponse(
        code = 500,
        type = "InternalServerError",
        message = exception?.message ?: "",
    )


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(exception: MethodArgumentNotValidException): String { //
        // TODO you can choose to return your custom object here, which will then get transformed to json/xml etc.
        return exception.message
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(EntityNotFound::class)
    fun handleEntityNotFound(exception: EntityNotFound) = ErrorResponse(
        code = 404,
        type = "EntityNotFound",
        message = exception.message,
        detail = exception.detail
    )

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(exception: ConstraintViolationException): String { //
        // TODO you can choose to return your custom object here, which will then get transformed to json/xml etc.
        return exception.message ?: ""
    }

}
