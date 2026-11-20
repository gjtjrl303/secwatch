package com.example.secwatch.notify

import com.example.secwatch.common.Severity
import com.example.secwatch.dependency.Dependency
import com.example.secwatch.dependency.DependencyRepository
import com.example.secwatch.finding.Finding
import com.example.secwatch.finding.FindingRepository
import com.example.secwatch.finding.FindingStatus
import com.example.secwatch.project.Project
import com.example.secwatch.project.ProjectRepository
import com.example.secwatch.support.FakeExternalApiIntegrationTest
import java.time.LocalDateTime
import java.util.concurrent.CyclicBarrier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@DisplayName("새 취약점 알림")
class NewFindingNotifierTest : FakeExternalApiIntegrationTest() {
    @Autowired lateinit var newFindingNotifier: NewFindingNotifier
    @Autowired lateinit var projectRepository: ProjectRepository
    @Autowired lateinit var dependencyRepository: DependencyRepository
    @Autowired lateinit var findingRepository: FindingRepository

    private val webhookUrl = "https://discord.com/api/webhooks/1/abc"
    private val notifyErrorMessage = "알림을 보내지 못했습니다. 다음 주기에 다시 시도합니다."
    private lateinit var project: Project
    private lateinit var dependency: Dependency

    @BeforeEach
    fun seed() {
        val newProject = Project("모꼬지", "m.example.com")
        newProject.changeWebhook(webhookUrl)
        project = projectRepository.save(newProject)
        dependency = dependencyRepository.save(Dependency(project, "npm", "lodash", "4.17.20"))
    }

    private fun saveFinding(
        vulnerabilityId: String,
        summary: String = "summary of $vulnerabilityId",
        severity: Severity = Severity.HIGH,
        notifiedAt: LocalDateTime? = null,
        status: FindingStatus = FindingStatus.NEW,
    ): Finding {
        val finding = Finding(
            project = project,
            dependency = dependency,
            primaryVulnerabilityId = vulnerabilityId,
            summary = summary,
            severity = severity,
            fixedVersions = "4.17.21",
            referenceUrl = "https://osv.dev/vulnerability/$vulnerabilityId",
            rawJson = "{}",
            firstSeenAt = LocalDateTime.now(),
        )
        if (notifiedAt != null) finding.markNotified(notifiedAt)
        if (status != FindingStatus.NEW) finding.changeStatus(status)
        return findingRepository.save(finding)
    }

    private fun notifiedCount() =
        jdbcTemplate.queryForObject("select count(*) from finding where notified_at is not null", Int::class.java)!!

    private fun lastNotifyError() =
        jdbcTemplate.queryForObject("select last_notify_error from project where id = ?", String::class.java, project.id)

    @Test
    @DisplayName("성공하면 메시지 한 통을 보내고 항목에 알린 시각을 기록한다")
    fun marks_notified_at_on_success_with_one_message() {
        saveFinding("GHSA-1")
        saveFinding("GHSA-2")

        newFindingNotifier.notifyPending(project.id!!)

        assertThat(fakeNotifier.sent).hasSize(1)
        assertThat(fakeNotifier.sent[0].first).isEqualTo(webhookUrl)
        assertThat(notifiedCount()).isEqualTo(2)
        assertThat(lastNotifyError()).isNull()
    }

    @Test
    @DisplayName("메시지에 프로젝트, 패키지, 위험도, 요약, 고쳐진 버전이 들어간다")
    fun message_names_project_package_severity_summary_and_fix() {
        saveFinding("GHSA-1", summary = "Command Injection in lodash")

        newFindingNotifier.notifyPending(project.id!!)

        val message = fakeNotifier.sent.single().second
        assertThat(message).contains("모꼬지", "lodash@4.17.20", "높음", "Command Injection in lodash", "4.17.21")
    }

    @Test
    @DisplayName("위험도가 높은 항목이 메시지의 앞에 나온다")
    fun more_severe_findings_come_first() {
        saveFinding("LOW-1", summary = "낮은 것", severity = Severity.LOW)
        saveFinding("CRITICAL-1", summary = "치명적인 것", severity = Severity.CRITICAL)

        newFindingNotifier.notifyPending(project.id!!)

        val message = fakeNotifier.sent.single().second
        assertThat(message.indexOf("치명적인 것")).isLessThan(message.indexOf("낮은 것"))
    }

    @Test
    @DisplayName("메시지는 2000자 안으로 줄이고 나머지는 외 N건으로 알린다")
    fun message_truncated_to_2000_chars_with_remainder_count() {
        repeat(100) { number -> saveFinding("GHSA-$number", summary = "x".repeat(150)) }

        newFindingNotifier.notifyPending(project.id!!)

        val message = fakeNotifier.sent.single().second
        assertThat(message.length).isLessThanOrEqualTo(2000)
        assertThat(message).containsPattern("외 \\d+건")
        assertThat(notifiedCount()).isEqualTo(100)
    }

    @Test
    @DisplayName("전송이 실패하면 알린 시각을 비워 두고 오류를 기록한다")
    fun send_failure_keeps_notified_at_null_and_records_the_error() {
        saveFinding("GHSA-1")
        fakeNotifier.fail = true

        newFindingNotifier.notifyPending(project.id!!)

        assertThat(notifiedCount()).isEqualTo(0)
        assertThat(lastNotifyError()).isEqualTo(notifyErrorMessage)
        assertThat(lastNotifyError()).doesNotContain("discord.com")
    }

    @Test
    @DisplayName("이미 알린 항목은 다시 보내지 않는다")
    fun already_notified_findings_are_not_sent_again() {
        saveFinding("GHSA-1", notifiedAt = LocalDateTime.now())

        newFindingNotifier.notifyPending(project.id!!)

        assertThat(fakeNotifier.sent).isEmpty()
    }

    @Test
    @DisplayName("알리지 못한 이전 항목은 다시 보내고 성공하면 오류 표시를 지운다")
    fun unnotified_older_findings_are_retried_and_clear_the_error() {
        saveFinding("GHSA-1")
        fakeNotifier.fail = true
        newFindingNotifier.notifyPending(project.id!!)
        assertThat(lastNotifyError()).isEqualTo(notifyErrorMessage)

        fakeNotifier.fail = false
        newFindingNotifier.notifyPending(project.id!!)

        assertThat(fakeNotifier.sent).hasSize(1)
        assertThat(notifiedCount()).isEqualTo(1)
        assertThat(lastNotifyError()).isNull()
    }

    @Test
    @DisplayName("새로 발견 상태인 항목만 보낸다")
    fun only_new_status_findings_are_sent() {
        saveFinding("GHSA-1", status = FindingStatus.NOT_APPLICABLE)

        newFindingNotifier.notifyPending(project.id!!)

        assertThat(fakeNotifier.sent).isEmpty()
    }

    @Test
    @DisplayName("웹훅이 없는 프로젝트는 보내지 않고 오류도 기록하지 않는다")
    fun project_without_webhook_sends_nothing_and_records_no_error() {
        jdbcTemplate.update("update project set webhook_url = null where id = ?", project.id)
        saveFinding("GHSA-1")

        newFindingNotifier.notifyPending(project.id!!)

        assertThat(fakeNotifier.sent).isEmpty()
        assertThat(notifiedCount()).isEqualTo(0)
        assertThat(lastNotifyError()).isNull()
    }

    @Test
    @DisplayName("없는 프로젝트는 아무것도 하지 않는다")
    fun unknown_project_does_nothing() {
        newFindingNotifier.notifyPending(999999)

        assertThat(fakeNotifier.sent).isEmpty()
    }

    @Test
    @DisplayName("전송하는 동안 사용자가 바꾼 상태를 덮어쓰지 않는다")
    fun status_change_during_send_is_not_overwritten() {
        val findingId = saveFinding("GHSA-1").id!!
        fakeNotifier.onSend = {
            Thread {
                jdbcTemplate.update("update finding set status = 'ACKNOWLEDGED' where id = ?", findingId)
            }.also { thread -> thread.start() }.join()
        }

        newFindingNotifier.notifyPending(project.id!!)

        assertThat(jdbcTemplate.queryForObject("select status from finding where id = ?", String::class.java, findingId))
            .isEqualTo("ACKNOWLEDGED")
        assertThat(notifiedCount()).isEqualTo(1)
    }

    @Test
    @DisplayName("동시에 실행해도 같은 항목은 한 번만 보낸다")
    fun concurrent_notifications_send_once() {
        saveFinding("GHSA-1")
        fakeNotifier.delayMillis = 400
        val barrier = CyclicBarrier(2)

        val threads = List(2) {
            Thread {
                barrier.await()
                newFindingNotifier.notifyPending(project.id!!)
            }.also { thread -> thread.start() }
        }
        threads.forEach { thread -> thread.join() }

        assertThat(fakeNotifier.sent).hasSize(1)
        assertThat(notifiedCount()).isEqualTo(1)
    }
}
