package com.example.secwatch.sitescan

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.finding.FindingStatus
import com.example.secwatch.project.ProjectRepository
import java.time.Duration
import java.time.LocalDateTime
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SiteScanRecorder(
    private val projectRepository: ProjectRepository,
    private val siteScanRepository: SiteScanRepository,
    private val siteFindingRepository: SiteFindingRepository,
    private val siteFindingStatusHistoryRepository: SiteFindingStatusHistoryRepository,
    @Value("\${secwatch.site-scan.min-interval:10m}") private val minInterval: Duration,
    @Value("\${secwatch.site-scan.stale-after:30m}") private val staleAfter: Duration,
) {
    @Transactional
    fun start(projectId: Long, scanType: SiteScanType = SiteScanType.BASELINE): SiteScan {
        // 잠그는 순서는 항상 전체 시작 잠금, 프로젝트, 점검 기록이다. 순서가 엇갈리면 교착이 난다.
        siteScanRepository.lockAllStarts()
        val project = projectRepository.findForUpdate(projectId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)
        val now = LocalDateTime.now()

        siteScanRepository.findByStatusForUpdate(SiteScanStatus.RUNNING).forEach { running ->
            // 앱이 꺼지면 진행 중으로 남은 기록이 다음 점검을 영영 막으므로, 오래된 것은 실패로 정리한다
            if (running.startedAt.isAfter(now.minus(staleAfter))) throw SecwatchException(ErrorCode.SCAN_IN_PROGRESS)
            running.fail(STALE_MESSAGE, now)
        }
        val lastSucceeded = siteScanRepository.findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId, SiteScanStatus.SUCCEEDED)
        if (lastSucceeded != null && lastSucceeded.startedAt.isAfter(now.minus(minInterval))) {
            throw SecwatchException(ErrorCode.SCAN_TOO_SOON)
        }
        return siteScanRepository.save(SiteScan(project, scanType, now))
    }

    @Transactional
    fun succeed(scanId: Long, rawJson: String, alerts: List<SiteAlert>) {
        siteScanRepository.lockProjectOf(scanId) ?: return
        // 이미 끝난 점검(오래돼서 실패로 정리된 것 포함)의 결과가 뒤늦게 오면 받지 않는다
        val scan = siteScanRepository.findForUpdate(scanId, SiteScanStatus.RUNNING) ?: return
        val now = LocalDateTime.now()
        val existing = siteFindingRepository.findByProjectIdForUpdate(scan.project.id!!).associateBy { finding -> finding.alertRef }

        alerts.distinctBy { alert -> alert.alertRef }.forEach { alert ->
            val finding = existing[alert.alertRef]
            if (finding == null) {
                siteFindingRepository.save(SiteFinding(scan.project, alert, now))
            } else {
                finding.seenAgain(alert, now)
                // 고쳤다고 표시한 문제가 다시 나왔으면 고쳐지지 않은 것이라 다시 연다. 해당 없음은 사람의 판단이라 그대로 둔다.
                if (finding.status == FindingStatus.RESOLVED) {
                    finding.changeStatus(FindingStatus.NEW)
                    siteFindingStatusHistoryRepository.save(SiteFindingStatusHistory(finding, FindingStatus.RESOLVED, FindingStatus.NEW, now))
                }
            }
        }
        scan.succeed(rawJson, now)
    }

    @Transactional
    fun fail(scanId: Long, errorMessage: String) {
        siteScanRepository.findForUpdate(scanId, SiteScanStatus.RUNNING)?.fail(errorMessage, LocalDateTime.now())
    }

    @Transactional
    fun changeStatus(siteFindingId: Long, newStatus: FindingStatus, projectId: Long): SiteFinding {
        val finding = siteFindingRepository.findForUpdate(siteFindingId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_SITE_FINDING)
        if (finding.project.id != projectId) throw SecwatchException(ErrorCode.NOT_FOUND_SITE_FINDING)

        val oldStatus = finding.status
        if (oldStatus != newStatus) {
            finding.changeStatus(newStatus)
            siteFindingStatusHistoryRepository.save(SiteFindingStatusHistory(finding, oldStatus, newStatus, LocalDateTime.now()))
        }
        return finding
    }

    @Transactional(readOnly = true)
    fun findings(projectId: Long): List<SiteFinding> =
        siteFindingRepository.findByProjectId(projectId)
            .sortedWith(compareBy<SiteFinding>({ finding -> finding.risk.ordinal }, { finding -> finding.id }))

    /** 오래돼서 [start]가 실패로 정리할 진행 중 기록은 도는 것으로 보지 않는다. 화면이 그런 기록 때문에 버튼을 막지 않게 하기 위해서다. */
    @Transactional(readOnly = true)
    fun isRunning(projectId: Long): Boolean {
        val running = siteScanRepository.findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId, SiteScanStatus.RUNNING) ?: return false
        return running.startedAt.isAfter(LocalDateTime.now().minus(staleAfter))
    }

    @Transactional(readOnly = true)
    fun latestSucceeded(projectId: Long): SiteScanSummary? =
        siteScanRepository.findFirstByProjectIdAndStatusOrderByStartedAtDesc(projectId, SiteScanStatus.SUCCEEDED)

    @Transactional(readOnly = true)
    fun scans(projectId: Long): List<SiteScanSummary> = siteScanRepository.findTop20ByProjectIdOrderByStartedAtDesc(projectId)

    private companion object {
        const val STALE_MESSAGE = "점검이 끝나지 않아 실패로 처리했습니다."
    }
}
