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

    @Transactional
    fun changeStatus(findingId: Long, newStatus: FindingStatus): Finding {
        val finding = findingRepository.findWithDependency(findingId) ?: throw SecwatchException(ErrorCode.NOT_FOUND_FINDING)

        val oldStatus = finding.status
        if (oldStatus != newStatus) {
            finding.changeStatus(newStatus)
            findingStatusHistoryRepository.save(FindingStatusHistory(finding, oldStatus, newStatus, LocalDateTime.now()))
        }
        return finding
    }
}
