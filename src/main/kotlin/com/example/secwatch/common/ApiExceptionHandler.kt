package com.example.secwatch.common

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

data class ApiError(val code: Int, val message: String)

@RestControllerAdvice(annotations = [RestController::class])
class ApiExceptionHandler {
    @ExceptionHandler(SecwatchException::class)
    fun handle(e: SecwatchException): ResponseEntity<ApiError> =
        ResponseEntity.status(e.errorCode.status).body(ApiError(e.errorCode.code, e.errorCode.message))
}
