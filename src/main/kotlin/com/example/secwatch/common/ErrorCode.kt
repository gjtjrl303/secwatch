package com.example.secwatch.common

import org.springframework.http.HttpStatus

enum class ErrorCode(val status: HttpStatus, val code: Int, val message: String) {
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, 40000, "잘못된 요청입니다."),
    INVALID_DOMAIN(HttpStatus.BAD_REQUEST, 40001, "올바른 도메인을 입력해주세요."),
    INVALID_WEBHOOK_URL(HttpStatus.BAD_REQUEST, 40002, "디스코드 웹훅 주소를 확인해주세요."),
    EMPTY_DEPENDENCIES(HttpStatus.BAD_REQUEST, 40003, "의존성 목록을 붙여넣거나 파일을 올려 주세요."),
    UNSUPPORTED_FORMAT(HttpStatus.BAD_REQUEST, 40004, "지원하지 않는 형식입니다. package-lock.json, Gradle dependencies 출력, CycloneDX SBOM 중 하나를 넣어 주세요."),
    INVALID_STATUS(HttpStatus.BAD_REQUEST, 40005, "올바르지 않은 상태입니다."),
    INVALID_SEVERITY(HttpStatus.BAD_REQUEST, 40006, "올바르지 않은 위험도입니다."),
    INVALID_NAME(HttpStatus.BAD_REQUEST, 40007, "프로젝트 이름은 1자 이상 100자 이하로 입력해주세요."),

    NOT_FOUND_PROJECT(HttpStatus.NOT_FOUND, 40401, "프로젝트를 찾을 수 없습니다."),
    NOT_FOUND_FINDING(HttpStatus.NOT_FOUND, 40402, "발견 항목을 찾을 수 없습니다."),
    NOT_FOUND_SITE_FINDING(HttpStatus.NOT_FOUND, 40403, "사이트 발견 항목을 찾을 수 없습니다."),

    DUPLICATE_DOMAIN(HttpStatus.CONFLICT, 40901, "이미 등록된 도메인입니다."),
    SCAN_IN_PROGRESS(HttpStatus.CONFLICT, 40902, "다른 사이트 점검이 진행 중입니다. 끝난 뒤에 다시 시도해 주세요."),

    SCAN_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, 42901, "방금 점검한 프로젝트입니다. 잠시 뒤에 다시 시도해 주세요."),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 41301, "파일이 너무 큽니다. 20MB 이하의 파일을 올려 주세요."),
}
