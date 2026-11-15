package com.example.secwatch.osv

import com.example.secwatch.common.PackageReference
import com.example.secwatch.common.Severity
import java.net.SocketTimeoutException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

@DisplayName("OSV 클라이언트")
class OsvClientTest {
    private val baseUrl = "https://osv.test"
    private lateinit var server: MockRestServiceServer
    private lateinit var client: OsvClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(baseUrl)
        server = MockRestServiceServer.bindTo(builder).build()
        client = OsvClient(builder.build())
    }

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    private fun npm(name: String, version: String) = PackageReference("npm", name, version)

    private fun emptyResults(count: Int) = """{"results":[${List(count) { "{}" }.joinToString(",")}]}"""

    @Test
    @DisplayName("패키지별로 취약점 번호를 묶어 돌려준다")
    fun maps_ids_per_package() {
        server.expect(requestTo("$baseUrl/v1/querybatch"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.queries[0].package.name").value("lodash"))
            .andExpect(jsonPath("$.queries[0].package.ecosystem").value("npm"))
            .andExpect(jsonPath("$.queries[0].version").value("4.17.20"))
            .andRespond(withSuccess(fixture("osv-querybatch.json"), MediaType.APPLICATION_JSON))

        val lodash = npm("lodash", "4.17.20")
        val other = npm("other", "1.0.0")
        val result = client.findVulnerabilityIds(listOf(lodash, other))

        assertThat(result[lodash]).containsExactly("GHSA-35jh-r3h4-6jhm")
        assertThat(result[other]).isEmpty()
        server.verify()
    }

    @Test
    @DisplayName("한 번에 1000개까지만 묶어서 요청한다")
    fun batches_at_most_1000_queries() {
        val packageReferences = (1..2500).map { number -> npm("p$number", "1.0.0") }
        listOf(1000, 1000, 500).forEach { size ->
            server.expect(requestTo("$baseUrl/v1/querybatch"))
                .andExpect(jsonPath("$.queries.length()").value(size))
                .andRespond(withSuccess(emptyResults(size), MediaType.APPLICATION_JSON))
        }

        val result = client.findVulnerabilityIds(packageReferences)

        assertThat(result).hasSize(2500)
        server.verify()
    }

    @Test
    @DisplayName("다음 페이지 토큰이 있으면 끝까지 따라간다")
    fun follows_next_page_token() {
        val packageReference = npm("lodash", "4.17.20")
        server.expect(requestTo("$baseUrl/v1/querybatch"))
            .andRespond(
                withSuccess(
                    """{"results":[{"vulns":[{"id":"A-1"}],"next_page_token":"tok"}]}""",
                    MediaType.APPLICATION_JSON
                )
            )
        server.expect(requestTo("$baseUrl/v1/querybatch"))
            .andExpect(jsonPath("$.queries[0].page_token").value("tok"))
            .andRespond(withSuccess("""{"results":[{"vulns":[{"id":"B-2"}]}]}""", MediaType.APPLICATION_JSON))

        val result = client.findVulnerabilityIds(listOf(packageReference))

        assertThat(result[packageReference]).containsExactly("A-1", "B-2")
        server.verify()
    }

    @Test
    @DisplayName("GHSA 기록에서 별칭, 요약, 위험도, 고쳐진 버전을 읽는다")
    fun parses_ghsa_record() {
        server.expect(requestTo("$baseUrl/v1/vulns/GHSA-35jh-r3h4-6jhm"))
            .andRespond(withSuccess(fixture("osv-vuln-ghsa.json"), MediaType.APPLICATION_JSON))

        val vulnerability = client.getVulnerability("GHSA-35jh-r3h4-6jhm")!!

        assertThat(vulnerability.id).isEqualTo("GHSA-35jh-r3h4-6jhm")
        assertThat(vulnerability.aliases).containsExactly("CVE-2021-23337")
        assertThat(vulnerability.summary).isEqualTo("Command Injection in lodash")
        assertThat(vulnerability.severity).isEqualTo(Severity.HIGH)
        assertThat(vulnerability.fixedVersions).containsExactly("4.17.21")
        assertThat(vulnerability.referenceUrl).isEqualTo("https://osv.dev/vulnerability/GHSA-35jh-r3h4-6jhm")
        assertThat(vulnerability.rawJson).contains("Command Injection in lodash")
    }

    @Test
    @DisplayName("위험도가 없으면 미분류로 읽는다")
    fun missing_severity_is_unclassified() {
        server.expect(requestTo("$baseUrl/v1/vulns/X-1"))
            .andRespond(withSuccess("""{"id":"X-1","summary":"s"}""", MediaType.APPLICATION_JSON))

        assertThat(client.getVulnerability("X-1")!!.severity).isEqualTo(Severity.UNCLASSIFIED)
    }

    @Test
    @DisplayName("철회된 기록은 null로 돌려준다")
    fun withdrawn_returns_null() {
        server.expect(requestTo("$baseUrl/v1/vulns/X-2"))
            .andRespond(
                withSuccess("""{"id":"X-2","withdrawn":"2024-02-02T00:00:00Z"}""", MediaType.APPLICATION_JSON)
            )

        assertThat(client.getVulnerability("X-2")).isNull()
    }

    @Test
    @DisplayName("서버 오류(500)는 사용 불가 예외로 바꾼다")
    fun http_500_throws_unavailable() {
        server.expect(requestTo("$baseUrl/v1/querybatch")).andRespond(withServerError())

        assertThrows<OsvUnavailableException> { client.findVulnerabilityIds(listOf(npm("a", "1.0.0"))) }
    }

    @Test
    @DisplayName("시간 초과는 사용 불가 예외로 바꾼다")
    fun timeout_throws_unavailable() {
        server.expect(requestTo("$baseUrl/v1/vulns/X-3")).andRespond { throw SocketTimeoutException("timeout") }

        assertThrows<OsvUnavailableException> { client.getVulnerability("X-3") }
    }

    @Test
    @DisplayName("상세 조회가 404나 410이면 null로 돌려준다(점검을 막지 않는다)")
    fun detail_404_and_410_return_null() {
        server.expect(requestTo("$baseUrl/v1/vulns/GONE-1")).andRespond(withStatus(HttpStatus.NOT_FOUND))
        server.expect(requestTo("$baseUrl/v1/vulns/GONE-2")).andRespond(withStatus(HttpStatus.GONE))

        assertThat(client.getVulnerability("GONE-1")).isNull()
        assertThat(client.getVulnerability("GONE-2")).isNull()
    }

    @Test
    @DisplayName("상세 조회가 429(요청 과다)이면 일시적 오류로 보고 예외를 던진다")
    fun detail_429_is_transient_and_throws_unavailable() {
        server.expect(requestTo("$baseUrl/v1/vulns/BUSY-1")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS))

        assertThrows<OsvUnavailableException> { client.getVulnerability("BUSY-1") }
    }

    @Test
    @DisplayName("200인데 JSON이 아닌 응답이 오면 사용 불가 예외로 바꾼다")
    fun non_json_200_body_throws_unavailable() {
        server.expect(requestTo("$baseUrl/v1/vulns/HTML-1"))
            .andRespond(withSuccess("<html>proxy error</html>", MediaType.TEXT_HTML))

        assertThrows<OsvUnavailableException> { client.getVulnerability("HTML-1") }
    }
}
