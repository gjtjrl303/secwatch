package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException

object WebhookUrl {
    private const val MAX_LENGTH = 500
    private val PREFIXES = listOf(
        "https://discord.com/api/webhooks/",
        "https://discordapp.com/api/webhooks/",
    )

    fun validate(raw: String): String {
        val url = raw.trim()
        val valid = PREFIXES.any { url.startsWith(it) && url.length > it.length } && url.none { it.isWhitespace() } && url.length <= MAX_LENGTH
        if (!valid) throw SecwatchException(ErrorCode.INVALID_WEBHOOK_URL)
        return url
    }
}
