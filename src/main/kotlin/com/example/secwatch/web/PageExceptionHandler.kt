package com.example.secwatch.web

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.servlet.ModelAndView

@ControllerAdvice(assignableTypes = [PageController::class])
class PageExceptionHandler {
    @ExceptionHandler(SecwatchException::class)
    fun handle(e: SecwatchException) = ModelAndView("error", mapOf("message" to e.message), e.errorCode.status)
}

// 크기 한도는 컨트롤러를 고르기 전에 걸려서, 컨트롤러를 지정한 위 처리기로는 잡히지 않는다
@ControllerAdvice
class UploadExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handleTooLarge(): ModelAndView {
        val errorCode = ErrorCode.FILE_TOO_LARGE
        return ModelAndView("error", mapOf("message" to errorCode.message), errorCode.status)
    }
}
