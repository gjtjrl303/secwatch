package com.example.secwatch.web

import com.example.secwatch.common.SecwatchException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

@ControllerAdvice(assignableTypes = [PageController::class])
class PageExceptionHandler {
    @ExceptionHandler(SecwatchException::class)
    fun handle(e: SecwatchException) = ModelAndView("error", mapOf("message" to e.message), e.errorCode.status)
}
