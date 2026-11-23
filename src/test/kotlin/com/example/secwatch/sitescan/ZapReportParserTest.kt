package com.example.secwatch.sitescan

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("ZAP 결과 읽기")
class ZapReportParserTest {
    private val parser = ZapReportParser(ObjectMapper())

    private fun fixture() = javaClass.getResource("/fixtures/zap-report-small.json")!!.readText()

    private fun report(vararg alerts: String) = """{"site":[{"@name":"http://a.example.com","alerts":[${alerts.joinToString(",")}]}]}"""

    private fun alert(ref: String = "1-1", extra: String = "") =
        """{"pluginid":"1","alertRef":"$ref","name":"n","riskcode":"2","desc":"<p>d</p>","solution":"<p>s</p>","reference":"","cweid":"79","instances":[{"uri":"http://a.example.com/"}]$extra}"""

    @Test
    @DisplayName("사이트가 여러 개로 나뉘어 있어도 모든 사이트의 항목을 읽는다")
    fun reads_alerts_from_every_site() {
        val alerts = parser.parse(fixture())

        assertThat(alerts.map { it.alertRef }).containsExactly("10095", "10038-1", "10110", "10109")
    }

    @Test
    @DisplayName("이름과 위험도와 유형 번호와 걸린 주소를 읽는다")
    fun reads_name_risk_cwe_and_urls() {
        val backup = parser.parse(fixture()).first()

        assertThat(backup.name).isEqualTo("Backup File Disclosure")
        assertThat(backup.risk).isEqualTo(SiteRisk.MEDIUM)
        assertThat(backup.cweId).isEqualTo(530)
        assertThat(backup.urls).hasSize(2).allSatisfy { url -> assertThat(url).startsWith("http://host.docker.internal:3000/") }
    }

    @Test
    @DisplayName("위험도 숫자를 네 단계로 옮기고 모르는 값은 정보로 둔다")
    fun maps_risk_codes() {
        val risks = listOf("3", "2", "1", "0", "9").map { code ->
            parser.parse(report(alert().replace("\"riskcode\":\"2\"", "\"riskcode\":\"$code\""))).single().risk
        }

        assertThat(risks).containsExactly(SiteRisk.HIGH, SiteRisk.MEDIUM, SiteRisk.LOW, SiteRisk.INFORMATIONAL, SiteRisk.INFORMATIONAL)
    }

    @Test
    @DisplayName("설명과 고치는 방법에서 HTML 태그를 떼고 문단은 줄바꿈으로 나눈다")
    fun strips_html_from_text() {
        val json = report(alert().replace("<p>d</p>", "<p>first &lt;script&gt;</p><p>second</p>"))

        val parsed = parser.parse(json).single()

        assertThat(parsed.description).isEqualTo("first <script>\nsecond")
        assertThat(parsed.solution).isEqualTo("s")
    }

    @Test
    @DisplayName("참고 주소는 한 줄에 하나씩 나눈다")
    fun splits_references_into_lines() {
        val backup = parser.parse(fixture()).first()

        assertThat(backup.reference.lines()).hasSizeGreaterThan(1).allSatisfy { line -> assertThat(line).startsWith("https://") }
    }

    @Test
    @DisplayName("유형 번호가 없거나 -1이면 비워 둔다")
    fun missing_cwe_is_null() {
        assertThat(parser.parse(fixture()).last().cweId).isNull()
        assertThat(parser.parse(report(alert().replace("\"cweid\":\"79\"", "\"cweid\":\"x\""))).single().cweId).isNull()
    }

    @Test
    @DisplayName("항목 번호가 없으면 규칙 번호를 쓰고 둘 다 없으면 건너뛴다")
    fun falls_back_to_plugin_id() {
        val withoutRef = alert().replace("\"alertRef\":\"1-1\",", "")
        val withoutBoth = withoutRef.replace("\"pluginid\":\"1\",", "")

        assertThat(parser.parse(report(withoutRef, withoutBoth)).map { it.alertRef }).containsExactly("1")
    }

    @Test
    @DisplayName("같은 주소가 여러 번 걸려 있어도 한 번만 남긴다")
    fun duplicate_urls_collapsed() {
        val json = report(alert(extra = "").replace("""[{"uri":"http://a.example.com/"}]""", """[{"uri":"http://a.example.com/"},{"uri":"http://a.example.com/"},{"uri":"http://a.example.com/x"}]"""))

        assertThat(parser.parse(json).single().urls).containsExactly("http://a.example.com/", "http://a.example.com/x")
    }

    @Test
    @DisplayName("항목이 하나도 없는 결과는 빈 목록이다")
    fun no_alerts_is_empty() {
        assertThat(parser.parse("""{"site":[{"@name":"http://a.example.com","alerts":[]}]}""")).isEmpty()
    }

    @Test
    @DisplayName("JSON이 아니거나 사이트 정보가 없으면 점검 실패로 본다")
    fun unreadable_report_fails() {
        listOf("not json", "{}", """{"site":"x"}""").forEach { raw ->
            assertThrows<SiteScanFailedException> { parser.parse(raw) }
        }
    }
}
