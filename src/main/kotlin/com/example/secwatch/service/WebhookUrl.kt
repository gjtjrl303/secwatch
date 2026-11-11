package com.example.secwatch.service

import com.example.secwatch.common.InvalidInputException

object WebhookUrl {
    private val PREFIXES = listOf(
        "https://discord.com/api/webhooks/",
        "https://discordapp.com/api/webhooks/",
    )

    fun validate(raw: String): String {
        val url = raw.trim()
        val valid = PREFIXES.any { url.startsWith(it) && url.length > it.length } && url.none { it.isWhitespace() }
        if (!valid) throw InvalidInputException("디스코드 웹훅 주소를 확인해주세요.")
        return url
    }
}
