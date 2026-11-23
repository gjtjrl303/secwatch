package com.example.secwatch.sitescan

interface SiteScanner {
    /** 주소를 점검하고 도구가 낸 원본 결과를 돌려준다. 점검하지 못하면 [SiteScanFailedException]을 던진다. */
    fun scan(targetUrl: String): String
}
