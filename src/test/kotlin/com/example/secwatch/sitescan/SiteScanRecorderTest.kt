package com.example.secwatch.sitescan

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.finding.FindingStatus
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("사이트 점검 기록과 발견 항목 저장")
class SiteScanRecorderTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var recorder: SiteScanRecorder

    private var projectId = 0L
    private var otherProjectId = 0L

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("insert into project(name, domain) values ('a', 'a.example.com'), ('b', 'b.example.com')")
        projectId = jdbcTemplate.queryForObject("select id from project where name = 'a'", Long::class.java)!!
        otherProjectId = jdbcTemplate.queryForObject("select id from project where name = 'b'", Long::class.java)!!
    }

    private fun alert(ref: String, name: String = "name of $ref", risk: SiteRisk = SiteRisk.MEDIUM, urls: List<String> = listOf("http://a.example.com/")) =
        SiteAlert(ref, name, risk, "description of $ref", "solution of $ref", "https://example.com/$ref", 693, urls)

    private fun scanRow(scanId: Long) = jdbcTemplate.queryForMap("select * from site_scan where id = ?", scanId)

    private fun findingRows() = jdbcTemplate.queryForList("select * from site_finding where project_id = ? order by alert_ref", projectId)

    private fun rewindScans(minutes: Int) =
        jdbcTemplate.update("update site_scan set started_at = date_sub(started_at, interval ? minute)", minutes)

    private fun errorOf(action: () -> Unit) = assertThrows<SecwatchException> { action() }.errorCode

    @Test
    @DisplayName("점검을 시작하면 진행 중인 기록이 만들어진다")
    fun start_creates_a_running_record() {
        val scan = recorder.start(projectId)

        val row = scanRow(scan.id!!)
        assertThat(row["status"]).isEqualTo("RUNNING")
        assertThat(row["scan_type"]).isEqualTo("BASELINE")
        assertThat(row["started_at"]).isNotNull()
        assertThat(row["finished_at"]).isNull()
    }

    @Test
    @DisplayName("없는 프로젝트는 점검을 시작할 수 없다")
    fun unknown_project_cannot_start() {
        assertThat(errorOf { recorder.start(999999) }).isEqualTo(ErrorCode.NOT_FOUND_PROJECT)
    }

    @Test
    @DisplayName("점검이 끝나면 원본 결과와 발견 항목을 저장하고 끝난 시각을 남긴다")
    fun succeed_stores_raw_result_and_findings() {
        val scan = recorder.start(projectId)

        recorder.succeed(scan.id!!, """{"site":[]}""", listOf(alert("10038-1", risk = SiteRisk.MEDIUM), alert("90004-1", risk = SiteRisk.LOW)))

        val row = scanRow(scan.id!!)
        assertThat(row["status"]).isEqualTo("SUCCEEDED")
        assertThat(row["raw_json"]).isEqualTo("""{"site":[]}""")
        assertThat(row["finished_at"]).isNotNull()
        assertThat(findingRows().map { finding -> listOf(finding["alert_ref"], finding["risk"], finding["status"], finding["cwe_id"]) })
            .containsExactly(listOf("10038-1", "MEDIUM", "NEW", 693), listOf("90004-1", "LOW", "NEW", 693))
    }

    @Test
    @DisplayName("다시 점검해서 같은 항목이 나오면 새로 만들지 않고 내용과 마지막으로 본 시각만 바꾸며 상태는 그대로 둔다")
    fun same_alert_is_updated_not_duplicated() {
        val first = recorder.start(projectId)
        recorder.succeed(first.id!!, "{}", listOf(alert("10038-1", name = "old name", urls = listOf("http://a.example.com/"))))
        val findingId = findingRows().single()["id"] as Long
        recorder.changeStatus(findingId, FindingStatus.ACKNOWLEDGED, projectId)
        val firstSeen = findingRows().single()["last_seen_at"]
        rewindScans(60)

        val second = recorder.start(projectId)
        recorder.succeed(second.id!!, "{}", listOf(alert("10038-1", name = "new name", urls = listOf("http://a.example.com/x", "http://a.example.com/y")), alert("10098-1")))

        val rows = findingRows()
        assertThat(rows.map { it["alert_ref"] }).containsExactly("10038-1", "10098-1")
        val updated = rows.first()
        assertThat(updated["id"]).isEqualTo(findingId)
        assertThat(updated["name"]).isEqualTo("new name")
        assertThat(updated["status"]).isEqualTo("ACKNOWLEDGED")
        assertThat(updated["instance_count"]).isEqualTo(2)
        assertThat(updated["sample_urls"] as String).contains("http://a.example.com/y")
        assertThat(updated["last_seen_at"]).isNotEqualTo(firstSeen)
    }

    @Test
    @DisplayName("이번 점검에서 나오지 않은 항목은 지우지 않는다")
    fun missing_alert_is_kept() {
        val first = recorder.start(projectId)
        recorder.succeed(first.id!!, "{}", listOf(alert("10038-1"), alert("10098-1")))
        rewindScans(60)

        val second = recorder.start(projectId)
        recorder.succeed(second.id!!, "{}", listOf(alert("10038-1")))

        assertThat(findingRows().map { it["alert_ref"] }).containsExactly("10038-1", "10098-1")
    }

    @Test
    @DisplayName("한 결과에 같은 항목이 두 번 있어도 하나만 저장한다")
    fun duplicate_alerts_in_one_result_are_collapsed() {
        val scan = recorder.start(projectId)

        recorder.succeed(scan.id!!, "{}", listOf(alert("10038-1"), alert("10038-1")))

        assertThat(findingRows()).hasSize(1)
    }

    @Test
    @DisplayName("이름이 저장할 수 있는 길이를 넘으면 잘라서 저장한다")
    fun long_name_is_truncated() {
        val scan = recorder.start(projectId)

        recorder.succeed(scan.id!!, "{}", listOf(alert("10038-1", name = "n".repeat(300))))

        assertThat(findingRows().single()["name"] as String).hasSize(255)
    }

    @Test
    @DisplayName("점검이 실패하면 실패 상태와 이유를 남기고 발견 항목은 만들지 않는다")
    fun fail_stores_the_reason() {
        val scan = recorder.start(projectId)

        recorder.fail(scan.id!!, "시간이 초과됐습니다.")

        val row = scanRow(scan.id!!)
        assertThat(row["status"]).isEqualTo("FAILED")
        assertThat(row["error_message"]).isEqualTo("시간이 초과됐습니다.")
        assertThat(row["finished_at"]).isNotNull()
        assertThat(findingRows()).isEmpty()
    }

    @Test
    @DisplayName("성공한 점검 뒤 정해진 간격 안에는 같은 프로젝트를 다시 점검할 수 없다")
    fun too_soon_after_a_successful_scan() {
        val scan = recorder.start(projectId)
        recorder.succeed(scan.id!!, "{}", emptyList())

        assertThat(errorOf { recorder.start(projectId) }).isEqualTo(ErrorCode.SCAN_TOO_SOON)
        assertThat(jdbcTemplate.queryForObject("select count(*) from site_scan", Int::class.java)).isEqualTo(1)
    }

    @Test
    @DisplayName("간격이 지나면 다시 점검할 수 있다")
    fun allowed_after_the_interval() {
        val scan = recorder.start(projectId)
        recorder.succeed(scan.id!!, "{}", emptyList())
        rewindScans(11)

        assertThat(recorder.start(projectId).id).isNotNull()
    }

    @Test
    @DisplayName("실패한 점검 뒤에는 기다리지 않고 다시 점검할 수 있다")
    fun allowed_right_after_a_failed_scan() {
        val scan = recorder.start(projectId)
        recorder.fail(scan.id!!, "실패")

        assertThat(recorder.start(projectId).id).isNotNull()
    }

    @Test
    @DisplayName("다른 프로젝트라도 진행 중인 점검이 있으면 시작할 수 없다")
    fun only_one_scan_runs_at_a_time() {
        recorder.start(otherProjectId)

        assertThat(errorOf { recorder.start(projectId) }).isEqualTo(ErrorCode.SCAN_IN_PROGRESS)
    }

    @Test
    @DisplayName("끝나지 않은 채 오래된 점검은 실패로 처리하고 새 점검을 시작한다")
    fun stale_running_scan_is_failed() {
        val stuck = recorder.start(otherProjectId)
        rewindScans(31)

        val scan = recorder.start(projectId)

        assertThat(scanRow(stuck.id!!)["status"]).isEqualTo("FAILED")
        assertThat(scanRow(scan.id!!)["status"]).isEqualTo("RUNNING")
    }

    @Test
    @DisplayName("오래돼서 실패로 정리된 점검의 결과가 뒤늦게 와도 받지 않는다")
    fun late_result_of_a_stale_scan_is_ignored() {
        val stuck = recorder.start(projectId)
        rewindScans(31)
        val retry = recorder.start(projectId)
        recorder.succeed(retry.id!!, """{"scan":"retry"}""", listOf(alert("10038-1", name = "from retry")))

        recorder.succeed(stuck.id!!, """{"scan":"stuck"}""", listOf(alert("10038-1", name = "from stuck"), alert("99999-1")))

        assertThat(scanRow(stuck.id!!)["status"]).isEqualTo("FAILED")
        assertThat(scanRow(stuck.id!!)["raw_json"]).isNull()
        assertThat(findingRows().map { finding -> listOf(finding["alert_ref"], finding["name"]) })
            .containsExactly(listOf("10038-1", "from retry"))
    }

    @Test
    @DisplayName("이미 끝난 점검은 성공과 실패를 다시 바꿀 수 없다")
    fun finished_scan_cannot_change_again() {
        val succeeded = recorder.start(projectId)
        recorder.succeed(succeeded.id!!, "{}", emptyList())
        rewindScans(11)
        val failed = recorder.start(projectId)
        recorder.fail(failed.id!!, "처음 실패")

        recorder.fail(succeeded.id!!, "뒤늦은 실패")
        recorder.fail(failed.id!!, "두 번째 실패")
        recorder.succeed(failed.id!!, "{}", listOf(alert("10038-1")))

        assertThat(scanRow(succeeded.id!!)["status"]).isEqualTo("SUCCEEDED")
        assertThat(scanRow(succeeded.id!!)["error_message"]).isNull()
        assertThat(scanRow(failed.id!!)["status"]).isEqualTo("FAILED")
        assertThat(scanRow(failed.id!!)["error_message"]).isEqualTo("처음 실패")
        assertThat(findingRows()).isEmpty()
    }

    @Test
    @DisplayName("상태를 바꾸면 저장하고 변경 이력을 남긴다")
    fun change_status_saves_history() {
        val scan = recorder.start(projectId)
        recorder.succeed(scan.id!!, "{}", listOf(alert("10038-1")))
        val findingId = findingRows().single()["id"] as Long

        recorder.changeStatus(findingId, FindingStatus.NOT_APPLICABLE, projectId)
        recorder.changeStatus(findingId, FindingStatus.NOT_APPLICABLE, projectId)

        assertThat(findingRows().single()["status"]).isEqualTo("NOT_APPLICABLE")
        val history = jdbcTemplate.queryForList("select from_status, to_status from site_finding_status_history where site_finding_id = ?", findingId)
        assertThat(history.map { row -> listOf(row["from_status"], row["to_status"]) }).containsExactly(listOf("NEW", "NOT_APPLICABLE"))
    }

    @Test
    @DisplayName("다른 프로젝트의 항목은 바꿀 수 없다")
    fun cannot_change_another_projects_finding() {
        val scan = recorder.start(projectId)
        recorder.succeed(scan.id!!, "{}", listOf(alert("10038-1")))
        val findingId = findingRows().single()["id"] as Long

        assertThat(errorOf { recorder.changeStatus(findingId, FindingStatus.RESOLVED, otherProjectId) }).isEqualTo(ErrorCode.NOT_FOUND_SITE_FINDING)
        assertThat(errorOf { recorder.changeStatus(999999, FindingStatus.RESOLVED, projectId) }).isEqualTo(ErrorCode.NOT_FOUND_SITE_FINDING)
    }

    @Test
    @DisplayName("발견 항목은 위험도가 높은 순서로 돌려준다")
    fun findings_are_listed_by_risk() {
        val scan = recorder.start(projectId)
        recorder.succeed(scan.id!!, "{}", listOf(alert("1-1", risk = SiteRisk.INFORMATIONAL), alert("2-1", risk = SiteRisk.HIGH), alert("3-1", risk = SiteRisk.LOW)))

        assertThat(recorder.findings(projectId).map { it.risk }).containsExactly(SiteRisk.HIGH, SiteRisk.LOW, SiteRisk.INFORMATIONAL)
    }
}
