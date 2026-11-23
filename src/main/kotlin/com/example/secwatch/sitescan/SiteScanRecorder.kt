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
        val project = projectRepository.findForUpdate(projectId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)
        val now = LocalDateTime.now()

        siteScanRepository.findByStatus(SiteScanStatus.RUNNING).forEach { running ->
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
        val scan = siteScanRepository.findById(scanId).orElseThrow()
        val now = LocalDateTime.now()
        val existing = siteFindingRepository.findByProjectId(scan.project.id!!).associateBy { finding -> finding.alertRef }

        alerts.distinctBy { alert -> alert.alertRef }.forEach { alert ->
            val finding = existing[alert.alertRef]
            if (finding == null) siteFindingRepository.save(SiteFinding(scan.project, alert, now)) else finding.seenAgain(alert, now)
        }
        scan.succeed(rawJson, now)
    }

    @Transactional
    fun fail(scanId: Long, errorMessage: String) {
        siteScanRepository.findById(scanId).orElseThrow().fail(errorMessage, LocalDateTime.now())
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

    @Transactional(readOnly = true)
    fun scans(projectId: Long): List<SiteScan> = siteScanRepository.findByProjectIdOrderByStartedAtDesc(projectId)

    private companion object {
        const val STALE_MESSAGE = "점검이 끝나지 않아 실패로 처리했습니다."
    }
}
