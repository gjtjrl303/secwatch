package com.example.secwatch.support

import com.example.secwatch.sitescan.SiteScanner
import java.util.concurrent.CopyOnWriteArrayList

class FakeSiteScanner : SiteScanner {
    val scanned: MutableList<String> = CopyOnWriteArrayList()
    var result: String = EMPTY_REPORT
    var failure: RuntimeException? = null
    var onScan: (() -> Unit)? = null

    fun reset() {
        scanned.clear()
        result = EMPTY_REPORT
        failure = null
        onScan = null
    }

    override fun scan(targetUrl: String): String {
        scanned.add(targetUrl)
        onScan?.invoke()
        failure?.let { throw it }
        return result
    }

    companion object {
        const val EMPTY_REPORT = """{"site":[{"@name":"http://localhost:3000","alerts":[]}]}"""
    }
}
