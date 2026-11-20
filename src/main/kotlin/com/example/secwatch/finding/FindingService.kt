package com.example.secwatch.finding

import com.example.secwatch.common.ErrorCode
import com.example.secwatch.common.SecwatchException
import com.example.secwatch.common.Severity
import com.example.secwatch.project.ProjectRepository
import java.time.LocalDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FindingService(
    private val projectRepository: ProjectRepository,
    private val findingRepository: FindingRepository,
    private val findingStatusHistoryRepository: FindingStatusHistoryRepository,
) {
    @Transactional(readOnly = true)
    fun list(projectId: Long, status: FindingStatus?, severity: Severity?): List<Finding> {
        if (!projectRepository.existsById(projectId)) throw SecwatchException(ErrorCode.NOT_FOUND_PROJECT)

        val findings = findingRepository.search(projectId, status, severity)
        return findings.sortedWith(compareBy<Finding>({ finding -> finding.severity.ordinal }, { finding -> finding.id }))
    }

    /** 프로젝트 번호별로 열린 항목(새로 발견, 확인함)의 위험도별 개수를 돌려준다. */
    @Transactional(readOnly = true)
    fun openSeverityCounts(): Map<Long, Map<Severity, Long>> =
        findingRepository.countBySeverity(FindingStatus.OPEN)
            .groupBy({ count -> count.projectId }, { count -> count.severity to count.total })
            .mapValues { (_, pairs) -> pairs.toMap() }

    @Transactional
    fun changeStatus(findingId: Long, newStatus: FindingStatus): Finding {
        val lockedFinding = findingRepository.findForUpdate(findingId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_FINDING)

        val oldStatus = lockedFinding.status
        if (oldStatus != newStatus) {
            lockedFinding.changeStatus(newStatus)
            findingStatusHistoryRepository.save(FindingStatusHistory(lockedFinding, oldStatus, newStatus, LocalDateTime.now()))
        }
        // 응답에 패키지 정보가 필요해서 의존성과 함께 다시 읽는다
        return findingRepository.findWithDependency(findingId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_FINDING)
    }
}
