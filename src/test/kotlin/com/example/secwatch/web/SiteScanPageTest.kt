package com.example.secwatch.web

import com.example.secwatch.sitescan.SiteScanFailedException
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.model
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.view

@AutoConfigureMockMvc
@DisplayName("상세 화면의 사이트 점검")
class SiteScanPageTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var mockMvc: MockMvc

    private var projectId = 0L
    private var outsideProjectId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("insert into project(name, domain) values ('local', 'localhost:3000'), ('outside', 'outside.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project where name = 'local'", Long::class.java)!!
        outsideProjectId = jdbcTemplate.queryForObject("select id from project where name = 'outside'", Long::class.java)!!
    }

    private fun fixture() = javaClass.getResource("/fixtures/zap-report-small.json")!!.readText()

    private fun report(name: String, ref: String = "1-1", cwe: String = "693") =
        """{"site":[{"@name":"http://localhost:3000","alerts":[{"pluginid":"1","alertRef":"$ref","name":"$name","riskcode":"2","desc":"<p>설명</p>","solution":"<p>고치는 방법</p>","reference":"<p>https://example.com/ref</p>","cweid":"$cwe","instances":[{"uri":"http://localhost:3000/a"}]}]}]}"""

    private fun page(id: Long = projectId) = mockMvc.perform(get("/projects/$id?tab=site"))

    private fun requestScan(id: Long = projectId) = mockMvc.perform(post("/projects/$id/site-scans"))

    private fun awaitNoRunningScan() = Awaitility.await().atMost(Duration.ofSeconds(15)).until {
        jdbcTemplate.queryForObject("select count(*) from site_scan where status = 'RUNNING'", Int::class.java) == 0
    }

    private fun scanAndWait(result: String) {
        fakeSiteScanner.result = result
        requestScan().andExpect(status().is3xxRedirection)
        awaitNoRunningScan()
    }

    private fun rewindScans(minutes: Int) =
        jdbcTemplate.update("update site_scan set started_at = date_sub(started_at, interval ? minute)", minutes)

    @Test
    @DisplayName("점검할 수 있는 프로젝트에는 점검 버튼과 무엇을 하는 점검인지 설명이 나온다")
    fun shows_the_scan_button_and_what_the_scan_does() {
        page()
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("사이트 점검")))
            .andExpect(content().string(containsString("action=\"/projects/$projectId/site-scans\"")))
            .andExpect(content().string(containsString("POST 폼은 제출하지 않습니다")))
            .andExpect(content().string(containsString("찾지 못합니다")))
    }

    @Test
    @DisplayName("점검 버튼 옆에 Basic 인증 아이디와 비밀번호 입력칸이 있다")
    fun scan_form_has_optional_credentials() {
        page()
            .andExpect(content().string(containsString("name=\"username\"")))
            .andExpect(content().string(containsString("type=\"password\" id=\"scanPassword\" name=\"password\"")))
            .andExpect(content().string(containsString("저장하지 않습니다")))
    }

    @Test
    @DisplayName("점검할 때 넣은 인증 정보는 점검 도구에만 넘기고 어디에도 저장하거나 보여주지 않는다")
    fun credentials_typed_at_scan_time_are_used_once() {
        mockMvc.perform(post("/projects/$projectId/site-scans").param("username", "scan-user").param("password", "typed-secret"))
            .andExpect(status().is3xxRedirection)
        awaitNoRunningScan()

        val target = fakeSiteScanner.scanned.single()
        assertThat(target.credentials?.username).isEqualTo("scan-user")
        assertThat(target.credentials?.password).isEqualTo("typed-secret")
        page().andExpect(content().string(not(containsString("typed-secret"))))
        val columns = jdbcTemplate.queryForList(
            "select column_name from information_schema.columns where table_schema = database() and table_name = 'project'", String::class.java,
        )
        assertThat(columns.map { it.lowercase() }).doesNotContain("site_auth_username", "site_auth_password")
    }

    @Test
    @DisplayName("다음 점검은 인증 정보를 다시 넣지 않으면 인증 없이 둘러본다")
    fun next_scan_without_credentials_has_none() {
        mockMvc.perform(post("/projects/$projectId/site-scans").param("username", "scan-user").param("password", "typed-secret"))
        awaitNoRunningScan()
        rewindScans(60)

        requestScan().andExpect(status().is3xxRedirection)
        awaitNoRunningScan()

        assertThat(fakeSiteScanner.scanned.map { it.credentials }.last()).isNull()
    }

    @Test
    @DisplayName("아이디와 비밀번호 중 하나만 넣으면 점검하지 않고 안내 문구를 보여주며, 비밀번호를 되돌려 그리지 않는다")
    fun partial_credentials_are_rejected() {
        mockMvc.perform(post("/projects/$projectId/site-scans").param("username", "").param("password", "typed-secret"))
            .andExpect(status().isOk)
            .andExpect(model().attribute("tab", "site"))
            .andExpect(content().string(containsString("사이트 점검 인증 정보를 확인해주세요.")))
            .andExpect(content().string(not(containsString("typed-secret"))))

        assertThat(fakeSiteScanner.scanned).isEmpty()
        assertThat(jdbcTemplate.queryForObject("select count(*) from site_scan", Int::class.java)).isZero()
    }

    @Test
    @DisplayName("허용 목록에 없는 도메인의 프로젝트에는 버튼 대신 안내가 나온다")
    fun project_outside_the_allow_list_has_no_button() {
        page(outsideProjectId)
            .andExpect(content().string(not(containsString("/projects/$outsideProjectId/site-scans"))))
            .andExpect(content().string(containsString("점검 허용 목록에 없어서")))
    }

    @Test
    @DisplayName("점검을 요청하면 사이트 점검 탭으로 돌아오고, 끝나면 발견 항목이 위험도와 함께 나온다")
    fun request_then_findings_are_listed() {
        fakeSiteScanner.result = fixture()

        requestScan().andExpect(status().is3xxRedirection).andExpect(redirectedUrl("/projects/$projectId?tab=site#site-scan"))
        awaitNoRunningScan()

        val html = page().andReturn().response.contentAsString
        assertThat(html).contains("Backup File Disclosure", "Content Security Policy (CSP) Header Not Set", "Dangerous JS Functions")
        assertThat(html).contains("보안 설정 미흡")
        assertThat(html).contains("http://host.docker.internal:3000/")
        assertThat(html.indexOf("Backup File Disclosure")).isLessThan(html.indexOf("Dangerous JS Functions"))
        assertThat(html).contains("성공")
    }

    @Test
    @DisplayName("항목마다 설명과 고치는 방법과 참고 주소가 나온다")
    fun finding_shows_description_solution_and_references() {
        scanAndWait(report("Some Alert"))

        page()
            .andExpect(content().string(containsString("설명")))
            .andExpect(content().string(containsString("고치는 방법")))
            .andExpect(content().string(containsString("https://example.com/ref")))
            .andExpect(content().string(containsString("http://localhost:3000/a")))
    }

    @Test
    @DisplayName("점검 결과에 든 HTML은 글자로만 나온다")
    fun html_in_scan_results_is_escaped() {
        scanAndWait(report("&lt;script&gt;alert(1)&lt;/script&gt;").replace("<p>설명</p>", "&lt;img src=x onerror=alert(1)&gt;"))

        page()
            .andExpect(content().string(not(containsString("<script>alert(1)</script>"))))
            .andExpect(content().string(not(containsString("<img src=x"))))
            .andExpect(content().string(containsString("&lt;img src=x onerror=alert(1)&gt;")))
    }

    @Test
    @DisplayName("점검이 실패하면 화면이 깨지지 않고 실패와 이유가 나온다")
    fun failed_scan_shows_the_reason() {
        fakeSiteScanner.failure = SiteScanFailedException("점검이 제한 시간 안에 끝나지 않았습니다.")
        requestScan()
        awaitNoRunningScan()

        page()
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("실패")))
            .andExpect(content().string(containsString("점검이 제한 시간 안에 끝나지 않았습니다.")))
    }

    @Test
    @DisplayName("방금 점검한 프로젝트를 다시 요청하면 화면에 안내 문구가 나온다")
    fun too_soon_shows_the_message() {
        scanAndWait(report("Some Alert"))

        requestScan()
            .andExpect(status().isOk)
            .andExpect(view().name("project-detail"))
            .andExpect(content().string(containsString("방금 점검한 프로젝트입니다.")))
            .andExpect(content().string(containsString("Some Alert")))
    }

    @Test
    @DisplayName("허용 목록에 없는 프로젝트에 점검을 요청하면 안내 문구가 나온다")
    fun request_outside_the_allow_list_shows_the_message() {
        requestScan(outsideProjectId)
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("점검이 허용되지 않은 주소입니다.")))
    }

    @Test
    @DisplayName("없는 프로젝트에 점검을 요청하면 404 오류 화면을 보여준다")
    fun request_for_unknown_project_shows_the_error_page() {
        requestScan(999999).andExpect(status().isNotFound).andExpect(view().name("error"))
    }

    @Test
    @DisplayName("점검이 도는 동안에는 진행 중 안내가 나오고 버튼을 누를 수 없다")
    fun running_scan_disables_the_button() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        fakeSiteScanner.onScan = {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        requestScan()
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()

        try {
            page()
                .andExpect(content().string(containsString("점검이 진행 중입니다.")))
                .andExpect(content().string(containsString("disabled")))
        } finally {
            release.countDown()
            awaitNoRunningScan()
        }
    }

    @Test
    @DisplayName("상태를 바꾸면 저장하고 그 항목 자리로 돌아온다")
    fun change_status_saves_and_returns_to_the_finding() {
        scanAndWait(report("Some Alert"))
        val findingId = jdbcTemplate.queryForObject("select id from site_finding", Long::class.java)!!

        mockMvc.perform(post("/projects/$projectId/site-findings/$findingId/status").param("status", "NOT_APPLICABLE"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/projects/$projectId?tab=site#sf$findingId"))

        assertThat(jdbcTemplate.queryForObject("select status from site_finding where id = ?", String::class.java, findingId)).isEqualTo("NOT_APPLICABLE")
        page().andExpect(content().string(containsString("id=\"sf$findingId\"")))
    }

    @Test
    @DisplayName("다른 프로젝트의 항목이나 잘못된 상태 값은 오류 화면을 보여준다")
    fun wrong_project_or_status_shows_the_error_page() {
        scanAndWait(report("Some Alert"))
        val findingId = jdbcTemplate.queryForObject("select id from site_finding", Long::class.java)!!

        mockMvc.perform(post("/projects/$outsideProjectId/site-findings/$findingId/status").param("status", "RESOLVED"))
            .andExpect(status().isNotFound)
            .andExpect(view().name("error"))
        mockMvc.perform(post("/projects/$projectId/site-findings/$findingId/status").param("status", "nope"))
            .andExpect(status().isBadRequest)
            .andExpect(content().string(containsString("올바르지 않은 상태입니다.")))
    }

    @Test
    @DisplayName("가장 최근 점검에서 나오지 않은 항목에는 그렇게 표시한다")
    fun finding_missing_from_the_latest_scan_is_marked() {
        scanAndWait(report("Old Alert", ref = "1-1"))
        rewindScans(60)
        jdbcTemplate.update("update site_finding set last_seen_at = date_sub(last_seen_at, interval 60 minute)")
        scanAndWait(report("New Alert", ref = "2-1"))

        val html = page().andReturn().response.contentAsString

        assertThat(html).contains("Old Alert", "New Alert")
        assertThat(html.split("최근 점검에서는 나오지 않았습니다").size - 1).isEqualTo(1)
    }

    @Test
    @DisplayName("요약 타일은 최근 점검에서 나온 항목만 센다")
    fun summary_tile_counts_only_findings_from_the_latest_scan() {
        scanAndWait(report("Old Alert", ref = "1-1"))
        rewindScans(60)
        jdbcTemplate.update("update site_finding set last_seen_at = date_sub(last_seen_at, interval 60 minute)")
        scanAndWait(report("New Alert", ref = "2-1"))

        page()
            .andExpect(model().attribute("latestSiteFindingCount", 1))
            .andExpect(content().string(containsString("최근 점검에서 발견 1종")))
    }

    @Test
    @DisplayName("최근 점검이 실패하면 요약 타일에 발견 항목 수를 붙이지 않는다")
    fun failed_latest_scan_has_no_count_on_the_tile() {
        scanAndWait(report("Some Alert"))
        rewindScans(60)
        fakeSiteScanner.failure = SiteScanFailedException("점검이 제한 시간 안에 끝나지 않았습니다.")
        requestScan().andExpect(status().is3xxRedirection)
        awaitNoRunningScan()

        page()
            .andExpect(content().string(containsString("최근 점검 실패")))
            .andExpect(content().string(not(containsString("최근 점검에서 발견"))))
    }

    @Test
    @DisplayName("실패한 점검이 많이 쌓여도 가장 최근 성공한 점검을 기준으로 나오지 않은 항목을 표시한다")
    fun latest_success_is_found_past_many_failures() {
        jdbcTemplate.update(
            "insert into site_scan(project_id, scan_type, status, started_at, finished_at, raw_json) values (?, 'BASELINE', 'SUCCEEDED', date_sub(now(6), interval 120 minute), date_sub(now(6), interval 119 minute), '{}')",
            projectId,
        )
        (1..20).forEach { minutesAgo ->
            jdbcTemplate.update(
                "insert into site_scan(project_id, scan_type, status, started_at, finished_at, error_message) values (?, 'BASELINE', 'FAILED', date_sub(now(6), interval ? minute), now(6), '실패')",
                projectId, minutesAgo,
            )
        }
        jdbcTemplate.update(
            "insert into site_finding(project_id, alert_ref, name, risk, description, solution, reference, cwe_id, instance_count, sample_urls, status, first_seen_at, last_seen_at) values (?, '1-1', 'Old Alert', 'MEDIUM', 'd', 's', '', null, 1, '', 'NEW', date_sub(now(6), interval 300 minute), date_sub(now(6), interval 300 minute))",
            projectId,
        )

        page().andExpect(content().string(containsString("최근 점검에서는 나오지 않았습니다")))
    }

    @Test
    @DisplayName("너무 오래 진행 중으로 남은 점검은 막지 않고 버튼을 다시 누를 수 있다")
    fun stale_running_scan_does_not_disable_the_button() {
        jdbcTemplate.update(
            "insert into site_scan(project_id, scan_type, status, started_at) values (?, 'BASELINE', 'RUNNING', date_sub(now(6), interval 31 minute))",
            projectId,
        )

        page()
            .andExpect(content().string(not(containsString("점검이 진행 중입니다."))))
            .andExpect(content().string(not(containsString("disabled"))))
        requestScan().andExpect(status().is3xxRedirection)
        awaitNoRunningScan()
    }

    @Test
    @DisplayName("점검한 적이 없으면 발견 항목 자리에 아직 점검하지 않았다고 나온다")
    fun never_scanned_shows_a_hint() {
        page().andExpect(content().string(containsString("아직 사이트 점검을 하지 않았습니다.")))
    }
}
