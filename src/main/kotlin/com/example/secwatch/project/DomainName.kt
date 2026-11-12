package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException

object DomainName {
    private const val MAX_LENGTH = 255
    private val PATTERN = Regex(
        "^(localhost|[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+)(:\\d{1,5})?$"
    )

    fun normalize(raw: String): String {
        val domain = raw.trim().lowercase()
        if (domain.length > MAX_LENGTH || !PATTERN.matches(domain)) throw SecwatchException(ErrorCode.INVALID_DOMAIN)
        return domain
    }
}
