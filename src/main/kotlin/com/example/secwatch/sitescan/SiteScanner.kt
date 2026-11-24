package com.example.secwatch.sitescan

import com.example.secwatch.project.SiteCredentials

class SiteScanTarget(val url: String, val credentials: SiteCredentials? = null) {
    override fun toString() = "SiteScanTarget(url=$url, credentials=${if (credentials == null) "없음" else "있음"})"
}

interface SiteScanner {
    /** 주소를 점검하고 도구가 낸 원본 결과를 돌려준다. 점검하지 못하면 [SiteScanFailedException]을 던진다. */
    fun scan(target: SiteScanTarget): String
}
