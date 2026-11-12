package com.example.secwatch.common

import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

data class ApiErrorResponse(val code: Int, val message: String)

@RestControllerAdvice(annotations = [RestController::class])
class ApiExceptionHandler {
    @ExceptionHandler(SecwatchException::class)
    fun handle(e: SecwatchException): ResponseEntity<ApiErrorResponse> = respond(e.errorCode)

    @ExceptionHandler(HttpMessageNotReadableException::class, MethodArgumentTypeMismatchException::class)
    fun handleBadRequest(): ResponseEntity<ApiErrorResponse> = respond(ErrorCode.INVALID_REQUEST)

    private fun respond(errorCode: ErrorCode): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(errorCode.status).body(ApiErrorResponse(errorCode.code, errorCode.message))
}
