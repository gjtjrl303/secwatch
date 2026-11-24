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
    INVALID_SITE_SCAN_PATH(HttpStatus.BAD_REQUEST, 40008, "시작 경로는 /로 시작하는 이 사이트 안의 경로로 입력해주세요."),
    INVALID_SITE_CREDENTIALS(HttpStatus.BAD_REQUEST, 40009, "사이트 점검 인증 정보를 확인해주세요. 아이디와 비밀번호는 100자 이하이고, 아이디에는 콜론(:)을 넣을 수 없습니다."),

    SCAN_TARGET_NOT_ALLOWED(HttpStatus.FORBIDDEN, 40301, "점검이 허용되지 않은 주소입니다. 설정의 허용 목록에 있는 주소만 점검할 수 있습니다."),

    NOT_FOUND_PROJECT(HttpStatus.NOT_FOUND, 40401, "프로젝트를 찾을 수 없습니다."),
    NOT_FOUND_FINDING(HttpStatus.NOT_FOUND, 40402, "발견 항목을 찾을 수 없습니다."),
    NOT_FOUND_SITE_FINDING(HttpStatus.NOT_FOUND, 40403, "사이트 발견 항목을 찾을 수 없습니다."),

    DUPLICATE_DOMAIN(HttpStatus.CONFLICT, 40901, "이미 등록된 도메인입니다."),
    SCAN_IN_PROGRESS(HttpStatus.CONFLICT, 40902, "다른 사이트 점검이 진행 중입니다. 끝난 뒤에 다시 시도해 주세요."),

    SCAN_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, 42901, "방금 점검한 프로젝트입니다. 잠시 뒤에 다시 시도해 주세요."),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 41301, "파일이 너무 큽니다. 20MB 이하의 파일을 올려 주세요."),
}
