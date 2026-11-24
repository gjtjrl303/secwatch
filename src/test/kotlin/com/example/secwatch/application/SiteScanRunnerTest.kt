package com.example.secwatch.application

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.sitescan.SiteScanFailedException
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("사이트 점검 실행")
class SiteScanRunnerTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var runner: SiteScanRunner

    private var projectId = 0L
    private var outsideProjectId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("insert into project(name, domain) values ('local', 'localhost:3000'), ('outside', 'outside.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project where name = 'local'", Long::class.java)!!
        outsideProjectId = jdbcTemplate.queryForObject("select id from project where name = 'outside'", Long::class.java)!!
    }

    private fun fixture() = javaClass.getResource("/fixtures/zap-report-small.json")!!.readText()

    private fun scanRow(scanId: Long) = jdbcTemplate.queryForMap("select * from site_scan where id = ?", scanId)

    private fun awaitFinished(scanId: Long): Map<String, Any?> {
        Awaitility.await().atMost(Duration.ofSeconds(15)).until { scanRow(scanId)["status"] != "RUNNING" }
        return scanRow(scanId)
    }

    private fun errorOf(action: () -> Unit) = assertThrows<SecwatchException> { action() }.errorCode

    @Test
    @DisplayName("점검을 요청하면 바로 번호를 돌려주고, 끝나면 결과와 발견 항목을 저장한다")
    fun request_runs_the_scan_and_stores_the_result() {
        fakeSiteScanner.result = fixture()

        val scanId = runner.request(projectId)

        val row = awaitFinished(scanId)
        assertThat(row["status"]).isEqualTo("SUCCEEDED")
        assertThat(row["raw_json"]).isEqualTo(fixture())
        assertThat(fakeSiteScanner.scanned).containsExactly("http://localhost:3000")
        val refs = jdbcTemplate.queryForList("select alert_ref from site_finding where project_id = ? order by alert_ref", String::class.java, projectId)
        assertThat(refs).containsExactly("10038-1", "10095", "10109", "10110")
    }

    @Test
    @DisplayName("점검 도구가 실패하면 실패 상태와 이유를 남긴다")
    fun scanner_failure_is_recorded() {
        fakeSiteScanner.failure = SiteScanFailedException("점검이 제한 시간 안에 끝나지 않았습니다.")

        val row = awaitFinished(runner.request(projectId))

        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["error_message"]).isEqualTo("점검이 제한 시간 안에 끝나지 않았습니다.")
    }

    @Test
    @DisplayName("결과를 읽지 못하면 실패로 남기고 발견 항목은 만들지 않는다")
    fun unreadable_result_is_recorded_as_failure() {
        fakeSiteScanner.result = "not json"

        val row = awaitFinished(runner.request(projectId))

        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["error_message"]).isEqualTo("ZAP 결과를 읽지 못했습니다.")
        assertThat(jdbcTemplate.queryForObject("select count(*) from site_finding", Int::class.java)).isEqualTo(0)
    }

    @Test
    @DisplayName("예상하지 못한 오류가 나도 진행 중으로 남지 않고 실패로 끝난다")
    fun unexpected_error_does_not_leave_the_scan_running() {
        fakeSiteScanner.failure = IllegalStateException("boom")

        val row = awaitFinished(runner.request(projectId))

        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["error_message"]).isEqualTo("점검 중 오류가 발생했습니다.")
    }

    @Test
    @DisplayName("허용 목록에 없는 도메인은 점검하지 않고 기록도 만들지 않는다")
    fun target_outside_the_allow_list_is_rejected() {
        assertThat(errorOf { runner.request(outsideProjectId) }).isEqualTo(ErrorCode.SCAN_TARGET_NOT_ALLOWED)

        assertThat(fakeSiteScanner.scanned).isEmpty()
        assertThat(jdbcTemplate.queryForObject("select count(*) from site_scan", Int::class.java)).isEqualTo(0)
    }

    @Test
    @DisplayName("없는 프로젝트는 점검할 수 없다")
    fun unknown_project() {
        assertThat(errorOf { runner.request(999999) }).isEqualTo(ErrorCode.NOT_FOUND_PROJECT)
    }

    @Test
    @DisplayName("점검이 도는 동안 다시 요청하면 진행 중 안내를 받는다")
    fun second_request_while_running_is_rejected() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        fakeSiteScanner.onScan = {
            started.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
        val scanId = runner.request(projectId)
        assertThat(started.await(10, TimeUnit.SECONDS)).isTrue()

        assertThat(errorOf { runner.request(projectId) }).isEqualTo(ErrorCode.SCAN_IN_PROGRESS)

        release.countDown()
        assertThat(awaitFinished(scanId)["status"]).isEqualTo("SUCCEEDED")
        assertThat(fakeSiteScanner.scanned).hasSize(1)
    }
}
