package com.example.secwatch.notify

import com.example.secwatch.finding.Finding
import com.example.secwatch.finding.FindingRepository
import com.example.secwatch.finding.FindingStatus
import com.example.secwatch.project.ProjectRepository
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Service
class NewFindingNotifier(
    private val projectRepository: ProjectRepository,
    private val findingRepository: FindingRepository,
    private val notifier: Notifier,
    transactionManager: PlatformTransactionManager,
) {
    private val log = LoggerFactory.getLogger(NewFindingNotifier::class.java)
    private val transactionTemplate = TransactionTemplate(transactionManager)

    /**
     * 프로젝트 행 잠금 안에서 아직 못 보낸 새 항목을 한 통으로 보낸다. 이전에 실패한 항목도 다시 보낸다.
     * 잠금을 쥔 채 보내는 것은 같은 항목을 두 번 알리지 않기 위해서이고, 전송 시간 제한(연결 5초, 읽기 10초)이 잠금 시간의 상한이다.
     */
    fun notifyPending(projectId: Long) {
        transactionTemplate.executeWithoutResult { _ ->
            val project = projectRepository.findForUpdate(projectId) ?: return@executeWithoutResult
            val webhookUrl = project.webhookUrl
            if (webhookUrl.isNullOrBlank()) return@executeWithoutResult

            val pendingFindings = findPendingFindings(projectId)
            if (pendingFindings.isEmpty()) return@executeWithoutResult

            try {
                notifier.send(webhookUrl, FindingMessageFormatter.format(project.name, pendingFindings))
            } catch (e: NotifyFailedException) {
                log.warn("알림을 보내지 못했습니다. projectId={}", projectId)
                project.markNotifyFailed(NOTIFY_ERROR)
                return@executeWithoutResult
            }

            project.markNotifySucceeded()
            val notifiedAt = LocalDateTime.now()
            for (finding in pendingFindings) {
                finding.markNotified(notifiedAt)
            }
        }
    }

    private fun findPendingFindings(projectId: Long): List<Finding> {
        val findings = findingRepository.findPendingNotification(projectId, FindingStatus.NEW)
        return findings.sortedWith(compareBy<Finding>({ finding -> finding.severity.ordinal }, { finding -> finding.id }))
    }

    companion object {
        const val NOTIFY_ERROR = "알림을 보내지 못했습니다. 다음 주기에 다시 시도합니다."
    }
}
