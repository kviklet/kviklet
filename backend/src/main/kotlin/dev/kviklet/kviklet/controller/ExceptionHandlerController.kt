package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.AlreadyExecutedException
import dev.kviklet.kviklet.service.InvalidReviewException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ErrorResponse(val message: String)

@ControllerAdvice
class ExceptionHandlerController {
    @ExceptionHandler(InvalidReviewException::class)
    fun handleInvalidRequest(ex: InvalidReviewException): ResponseEntity<ErrorResponse> {
        return ResponseEntity(ErrorResponse(ex.message ?: "Unknown Error"), HttpStatus.BAD_REQUEST)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ExceptionHandlerController::class.java)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleNotValidblabla(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<Any> {
        // Log the exception details
        logger.error("Validation error at ${request.requestURI}: ${ex.message}")
        // Return a meaningful response or handle it as required
        return ResponseEntity("Validation error", HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        logger.error("JSON parse error at ${request.requestURI}: ${ex.message}")
        return ResponseEntity(ErrorResponse("JSON parse error"), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Access denied at ${request.requestURI}", ex)
        return ResponseEntity(ErrorResponse("Access denied"), HttpStatus.FORBIDDEN)
    }

    @ExceptionHandler(AlreadyExecutedException::class)
    fun handleAlreadyExecutedException(ex: AlreadyExecutedException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Already executed at ${request.requestURI}", ex)
        return ResponseEntity(ErrorResponse(ex.message ?: "Already Executed"), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Exception occurred at ${request.requestURI}", ex)
        return ResponseEntity(ErrorResponse("An unexpected error occurred :("), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
