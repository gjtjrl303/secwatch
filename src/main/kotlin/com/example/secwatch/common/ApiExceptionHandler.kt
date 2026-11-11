package com.example.secwatch.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(val message: String)

@RestControllerAdvice(annotations = [org.springframework.web.bind.annotation.RestController::class])
class ApiExceptionHandler {
    @ExceptionHandler(InvalidInputException::class)
    fun invalid(e: InvalidInputException) = respond(HttpStatus.BAD_REQUEST, e)

    @ExceptionHandler(UnsupportedFormatException::class)
    fun unsupported(e: UnsupportedFormatException) = respond(HttpStatus.BAD_REQUEST, e)

    @ExceptionHandler(ConflictException::class)
    fun conflict(e: ConflictException) = respond(HttpStatus.CONFLICT, e)

    @ExceptionHandler(NotFoundException::class)
    fun notFound(e: NotFoundException) = respond(HttpStatus.NOT_FOUND, e)

    private fun respond(status: HttpStatus, e: RuntimeException): ResponseEntity<ApiError> =
        ResponseEntity.status(status).body(ApiError(e.message ?: ""))
}
