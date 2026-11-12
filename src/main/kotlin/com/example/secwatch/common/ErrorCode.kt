package com.example.secwatch.common

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val code: Int, val message: String) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, 40000, "잘못된 요청입니다."),
    INVALID_DOMAIN(HttpStatus.BAD_REQUEST, 40001, "올바른 도메인을 입력해주세요."),
    INVALID_WEBHOOK_URL(HttpStatus.BAD_REQUEST, 40002, "디스코드 웹훅 주소를 확인해주세요."),
    EMPTY_DEPENDENCIES(HttpStatus.BAD_REQUEST, 40003, "의존성 목록을 붙여넣어 주세요."),
    UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, 40004, "지원하지 않는 형식입니다. package-lock.json 내용을 붙여넣어 주세요."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, 40007, "프로젝트 이름은 1자 이상 100자 이하로 입력해주세요."),

    NOT_FOUND_PROJECT(HttpStatus.NOT_FOUND, 40401, "프로젝트를 찾을 수 없습니다."),

    DUPLICATE_DOMAIN(HttpStatus.CONFLICT, 40901, "이미 등록된 도메인입니다."),
}
