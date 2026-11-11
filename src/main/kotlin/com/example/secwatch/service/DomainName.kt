package com.example.secwatch.service

import com.example.secwatch.common.InvalidInputException

object DomainName {
    private val PATTERN = Regex(
        "^(localhost|[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+)(:\\d{1,5})?$"
    )

    fun normalize(raw: String): String {
        val domain = raw.trim().lowercase()
        if (!PATTERN.matches(domain)) throw InvalidInputException("올바른 도메인을 입력해주세요.")
        return domain
    }
}
