package com.example.secwatch.project

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException

class SiteCredentials(val username: String, val password: String) {
    override fun toString() = "SiteCredentials(username=$username, password=******)"
}

object SiteScanSettings {
    private const val MAX_PATH_LENGTH = 200
    private const val MAX_CREDENTIAL_LENGTH = 100

    /** 비우거나 "/"만 넣으면 첫 화면부터 점검한다는 뜻으로 null을 돌려준다. */
    fun path(raw: String): String? {
        val path = raw.trim()
        if (path.isEmpty() || path == "/") return null
        val staysOnSite = path.startsWith("/") && !path.startsWith("//") && "://" !in path
        if (!staysOnSite || path.any { character -> character.isWhitespace() } || path.length > MAX_PATH_LENGTH) {
            throw SecwatchException(ErrorCode.INVALID_SITE_SCAN_PATH)
        }
        return path
    }

    /** 점검할 때 받은 값이다. 둘 다 비우면 인증 없이 점검하고, 하나만 넣으면 거부한다. */
    fun optionalCredentials(rawUsername: String, password: String): SiteCredentials? =
        if (rawUsername.isBlank() && password.isEmpty()) null else credentials(rawUsername, password)

    fun credentials(rawUsername: String, password: String): SiteCredentials {
        val username = rawUsername.trim()
        val usernameOk = username.isNotEmpty() && ':' !in username && username.none { character -> character.isISOControl() }
        val passwordOk = password.isNotEmpty() && password.none { character -> character.isISOControl() }
        if (!usernameOk || !passwordOk || username.length > MAX_CREDENTIAL_LENGTH || password.length > MAX_CREDENTIAL_LENGTH) {
            throw SecwatchException(ErrorCode.INVALID_SITE_CREDENTIALS)
        }
        return SiteCredentials(username, password)
    }
}
