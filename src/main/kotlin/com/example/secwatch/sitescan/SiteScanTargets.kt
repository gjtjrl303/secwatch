package com.example.secwatch.sitescan

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// 소유 확인(#47)을 붙이기 전까지는 운영자가 설정에 적은 주소만 점검한다
@Component
class SiteScanTargets(@Value("\${secwatch.site-scan.allowed-hosts:localhost}") allowedHosts: List<String>) {
    private val allowedHosts = allowedHosts.map { host -> host.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun urlFor(domain: String): String {
        val host = domain.substringBefore(':').lowercase()
        if (host !in allowedHosts) throw SecwatchException(ErrorCode.SCAN_TARGET_NOT_ALLOWED)
        val scheme = if (host in LOCAL_HOSTS) "http" else "https"
        return "$scheme://$domain"
    }

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")
    }
}
