package com.example.secwatch.service

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException

object DomainName {
    private val PATTERN = Regex(
        "^(localhost|[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+)(:\\d{1,5})?$"
    )

    fun normalize(raw: String): String {
        val domain = raw.trim().lowercase()
        if (!PATTERN.matches(domain)) throw SecwatchException(ErrorCode.INVALID_DOMAIN)
        return domain
    }
}
