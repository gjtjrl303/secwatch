package com.example.secwatch.sitescan

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

// 허용 목록이 비어 있으면 모든 주소를 점검한다. 배포할 때는 목록을 채우거나 소유 확인(#47)을 붙인다.
@Component
class SiteScanTargets(@Value("\${secwatch.site-scan.allowed-hosts:}") allowedHosts: List<String>) {
    private val allowedHosts = allowedHosts.map { host -> host.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    fun isAllowed(domain: String): Boolean = allowedHosts.isEmpty() || domain.substringBefore(':').lowercase() in allowedHosts

    fun urlFor(domain: String, path: String? = null): String {
        val host = domain.substringBefore(':').lowercase()
        if (!isAllowed(domain)) throw SecwatchException(ErrorCode.SCAN_TARGET_NOT_ALLOWED)
        val scheme = if (host in LOCAL_HOSTS) "http" else "https"
        return "$scheme://$domain${path.orEmpty()}"
    }

    private companion object {
        val LOCAL_HOSTS = setOf("localhost", "127.0.0.1")
    }
}
