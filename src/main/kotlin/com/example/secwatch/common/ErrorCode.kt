package com.example.secwatch.common

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val code: Int, val message: String) {
    INVALID_DOMAIN(HttpStatus.BAD_REQUEST, 40001, "올바른 도메인을 입력해주세요."),
    INVALID_WEBHOOK_URL(HttpStatus.BAD_REQUEST, 40002, "디스코드 웹훅 주소를 확인해주세요."),

    NOT_FOUND_SERVICE(HttpStatus.NOT_FOUND, 40401, "서비스를 찾을 수 없습니다."),

    DUPLICATE_DOMAIN(HttpStatus.CONFLICT, 40901, "이미 등록된 도메인입니다."),
}
