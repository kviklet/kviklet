package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.InvalidReviewException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ErrorResponse(val message: String)

@ControllerAdvice
class ExceptionHandlerController {
    @ExceptionHandler(InvalidReviewException::class)
    fun handleInvalidRequest(ex: InvalidReviewException): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ex.message ?: "Unknown Error"), HttpStatus.BAD_REQUEST)
    }
}
