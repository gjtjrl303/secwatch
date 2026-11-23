package com.example.secwatch.sitescan

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.util.HtmlUtils

class SiteScanFailedException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Component
class ZapReportParser(private val objectMapper: ObjectMapper) {

    fun parse(rawJson: String): List<SiteAlert> {
        val sites = try {
            objectMapper.readTree(rawJson)?.get("site")
        } catch (e: JsonProcessingException) {
            throw SiteScanFailedException(UNREADABLE, e)
        }
        if (sites == null || !sites.isArray) throw SiteScanFailedException(UNREADABLE)

        // 같은 주소라도 http와 https가 서로 다른 사이트로 나뉘어 올 수 있다
        return sites.flatMap { site -> site.path("alerts").toList() }.mapNotNull { alert -> toSiteAlert(alert) }
    }

    private fun toSiteAlert(alert: JsonNode): SiteAlert? {
        val alertRef = alert.text("alertRef").ifBlank { alert.text("pluginid") }
        if (alertRef.isBlank()) return null

        return SiteAlert(
            alertRef = alertRef,
            name = alert.text("name").ifBlank { alert.text("alert") },
            risk = RISK_BY_CODE[alert.text("riskcode")] ?: SiteRisk.INFORMATIONAL,
            description = plainText(alert.text("desc")),
            solution = plainText(alert.text("solution")),
            reference = plainText(alert.text("reference")),
            cweId = alert.text("cweid").toIntOrNull()?.takeIf { it > 0 },
            urls = alert.path("instances").map { instance -> instance.text("uri") }.filter { it.isNotBlank() }.distinct(),
        )
    }

    private fun JsonNode.text(field: String): String = path(field).asText("")

    private fun plainText(html: String): String =
        HtmlUtils.htmlUnescape(html.replace(PARAGRAPH_END, "\n").replace(TAG, "")).lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")

    private companion object {
        const val UNREADABLE = "ZAP 결과를 읽지 못했습니다."
        val RISK_BY_CODE = mapOf("3" to SiteRisk.HIGH, "2" to SiteRisk.MEDIUM, "1" to SiteRisk.LOW, "0" to SiteRisk.INFORMATIONAL)
        val PARAGRAPH_END = Regex("</p>|<br\\s*/?>", RegexOption.IGNORE_CASE)
        val TAG = Regex("<[^>]+>")
    }
}
