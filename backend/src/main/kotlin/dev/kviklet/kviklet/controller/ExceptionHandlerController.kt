package dev.kviklet.kviklet.controller

import dev.kviklet.kviklet.service.AlreadyExecutedException
import dev.kviklet.kviklet.service.EmailAlreadyExistsException
import dev.kviklet.kviklet.service.InvalidReviewException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

data class ErrorResponse(val message: String)

@ControllerAdvice
class ExceptionHandlerController {
    @ExceptionHandler(InvalidReviewException::class)
    fun handleInvalidRequest(ex: InvalidReviewException): ResponseEntity<ErrorResponse> =
        ResponseEntity(ErrorResponse(ex.message ?: "Unknown Error"), HttpStatus.BAD_REQUEST)

    companion object {
        private val logger = LoggerFactory.getLogger(ExceptionHandlerController::class.java)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleNotValid(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Validation error at ${request.requestURI}: ${ex.message}")
        return ResponseEntity("Validation error", HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Illegal argument at ${request.requestURI}: ${ex.message}")
        return ResponseEntity(ErrorResponse(ex.message ?: "Illegal argument"), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        logger.error("JSON parse error at ${request.requestURI}: ${ex.message}")
        return ResponseEntity(ErrorResponse("JSON parse error"), HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExistsException(
        ex: EmailAlreadyExistsException,
        request: HttpServletRequest,
    ): ResponseEntity<Any> {
        logger.error("Email already exists at ${request.requestURI}", ex)
        return ResponseEntity(ErrorResponse(ex.message ?: "Email already exists"), HttpStatus.BAD_REQUEST)
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

    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentialsException(ex: BadCredentialsException, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Bad credentials at ${request.requestURI}", ex)
        return ResponseEntity(ErrorResponse(ex.message ?: "Bad Credentials"), HttpStatus.UNAUTHORIZED)
    }

    @ExceptionHandler(Exception::class)
    fun handleAllExceptions(ex: Exception, request: HttpServletRequest): ResponseEntity<Any> {
        logger.error("Exception occurred at ${request.requestURI}", ex)
        return ResponseEntity(ErrorResponse("An unexpected error occurred :("), HttpStatus.INTERNAL_SERVER_ERROR)
    }
}
